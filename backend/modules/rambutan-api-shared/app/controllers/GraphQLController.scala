package controllers

import models._
import javax.inject._
import silvousplay.api.API
import silvousplay.imports._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import play.api.libs.json._

import sangria.execution._
import sangria.parser.{ QueryParser, SyntaxError }
import sangria.marshalling.playJson._
import sangria.renderer.SchemaRenderer
import sangria.slowlog.SlowLog

// models
import scala.util.{ Failure, Success }
import graphql.RambutanContext
import services._

// Websocket stuff
import play.api.mvc.WebSocket
import akka.actor.{ Actor, Props, ActorRef }
import services.EventMessage
import akka.actor.PoisonPill
import akka.pattern.ask
import akka.util.Timeout
import sangria.ast.OperationType
import sangria.marshalling.ScalaInput
import sangria.util.tag._
import akka.stream.scaladsl.SourceQueue
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Keep
import scala.util.Try

object GraphQLWS {
  case class Subscribe(query: String, operation: Option[String])

  implicit val subscribeReads = Json.reads[Subscribe]

  case class FullPayload[T: Reads](id: String, `type`: String, payload: T)

  implicit def reads[T: Reads] = Json.reads[FullPayload[T]]
}

object GraphQLSubscriptionActor {
  sealed trait ExternalMessage

  case class Subscribe(id: String, query: String, operation: Option[String]) extends ExternalMessage
  case class ConnectionInit() extends ExternalMessage

  // Internal
  case class Filter(item: EventMessage)
  case class PreparedQueryContext(id: String, query: PreparedQuery[RambutanContext, Any, Map[String, Any] @@ ScalaInput])
}

// This just holds onto the subscription state and does filtering
// We need to use an actor cuz it's a two way flow
class GraphQLSubscriptionActor(ctx: RambutanContext, errors: SourceQueue[JsValue]) extends Actor {
  // needs to handle Subscribe message (which changes our filter)

  // needs to handle Ask message
  implicit val ec = context.system.dispatcher
  val executor = Executor(graphql.SchemaDefinition.RambutanSchema)
  var subscriptions = Map.empty[String, GraphQLSubscriptionActor.PreparedQueryContext]

  def receive = {
    case GraphQLSubscriptionActor.Subscribe(id, query, operation) => {
      // do subscribe fire and forget
      QueryParser.parse(query) match {
        case Success(ast) => {
          ast.operationType(operation) match {
            case Some(OperationType.Subscription) => {
              executor.prepare(ast, ctx, (), operation).map { query =>
                self ! GraphQLSubscriptionActor.PreparedQueryContext(id, query)
              }.recover {
                case t: Throwable => {
                  println("ERROR PARSING", t.getMessage())
                  errors.offer(Json.obj(
                    "error" -> t.getMessage()))
                }
              }
            }
            case x => {
              // need to fix error protocol
              errors.offer(Json.obj(
                "type" -> "query",
                "error" -> s"OperationType: $x not supported with WebSockets. Use HTTP POST"))
            }
          }
        }
        case Failure(e) => {
          errors.offer(Json.obj(
            "type" -> "unknown",
            "error" -> e.getMessage()))
        }
      }
    }
    case context @ GraphQLSubscriptionActor.PreparedQueryContext(id, query) => {
      println(s"Query is prepared: ${query}")
      subscriptions = subscriptions.updated(id, context)
    }

    case GraphQLSubscriptionActor.ConnectionInit() => {
      errors.offer(Json.obj(
        "type" -> "connection_ack"))
    }

    case GraphQLSubscriptionActor.Filter(item) => {
      val s = sender()
      Future.sequence {
        // for efficiency, do we want to do a pre-filtering?
        subscriptions.map {
          case (id, ctx) => {
            val allFields = ctx.query.fields.map(_.field.name)
            // println(, item.eventType.identifier)
            ctx.query.execute(root = item).map { item =>
              // check data for fields we're looking for
              val ff = allFields.flatMap { f =>
                (item \ "data" \ f).asOpt[JsObject]
              }
              if (ff.length > 0) {
                Some(Json.obj(
                  "type" -> "next",
                  "id" -> id,
                  "payload" -> item))
              } else {
                None
              }
            }
          }
        }
      }.map { r =>
        s ! r.flatten.toList
      }
    }
  }

}

@Singleton
class GraphQLController @Inject() (
  configuration:   play.api.Configuration,
  socketService:   SocketService,
  rambutanContext: RambutanContext)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  // Default JsValue socket is a little too strict. If I send a bad msg, it dies. Frowntown.
  def socket() = WebSocket.acceptOrResult[String, String] { request =>
    println("SOCKET")
    for {
      socket <- socketService.openSocket(List(-1))
      (errorsQueue, errors) = Source
        .queue[JsValue](1000, OverflowStrategy.dropHead).preMaterialize()
      subscriptionActor = as.actorOf(Props(classOf[GraphQLSubscriptionActor], rambutanContext, errorsQueue))
    } yield {
      // pushes subscribe messages
      // TODO: this also needs to push back error messages. Idea: another SourceQueue

      val sink = Flow[String]
        .mapAsync(1) { i =>
          Try(Json.parse(i)) match {
            case Success(value) => {
              println(value)
              Future.successful(List(value))
            }
            case Failure(value) => {
              errorsQueue.offer(Json.obj(
                "error" -> "parsing",
                "raw" -> i)).map { _ => Nil }
            }
          }
        }.mapConcat(identity)
        .mapConcat { input =>
          val trySubscribe = Json.fromJson[GraphQLWS.FullPayload[GraphQLWS.Subscribe]](input)
          val tryAck = (input \ "type").asOpt[String]
          println("try", trySubscribe, input)
          (trySubscribe, tryAck) match {
            case (JsSuccess(subscription, _), _) if subscription.`type` =?= "subscribe" => {
              println("SUBSCRIBED", subscription.payload.query)
              List {
                GraphQLSubscriptionActor.Subscribe(subscription.id, subscription.payload.query, subscription.payload.operation)
              }
            }
            case (_, Some("connection_init")) => List(GraphQLSubscriptionActor.ConnectionInit())
            case _                            => Nil
          }
        }.to(Sink.actorRef[GraphQLSubscriptionActor.ExternalMessage](subscriptionActor, PoisonPill))

      val source = (socket.mapAsync(1) { v =>
        // filtering is handled at query level
        implicit val timeout = Timeout(100.milliseconds)
        (subscriptionActor ? GraphQLSubscriptionActor.Filter(v))
      }.mapConcat { m =>
        m.asInstanceOf[List[JsValue]]
      }).merge(errors).merge {
        Source.tick(1.second, 20.second, Json.toJson(Map("type" -> JsString("ping"))))
      }.map(Json.stringify)
      Right(Flow.fromSinkAndSource(sink, source))
    }
  }

  def graphqlBody() = Action.async(parse.json) { request =>
    println(request.body)
    val query = (request.body \ "query").as[String]
    val operation = (request.body \ "operationName").asOpt[String]

    val variables = (request.body \ "variables").toOption.flatMap {
      case JsString(vars) => Some(parseVariables(vars))
      case obj: JsObject  => Some(obj)
      case _              => None
    }

    executeQuery(query, variables, operation, false) // isTracingEnabled
  }

  lazy val exceptionHandler = ExceptionHandler {
    case (_, error @ TooComplexQueryError)         => HandledException(error.getMessage)
    case (_, error @ MaxQueryDepthReachedError(_)) => HandledException(error.getMessage)
  }

  case object TooComplexQueryError extends Exception("Query is too expensive.")

  private def parseVariables(variables: String) =
    if (variables.trim == "" || variables.trim == "null") Json.obj() else Json.parse(variables).as[JsObject]

  private def executeQuery(query: String, variables: Option[JsObject], operation: Option[String], tracing: Boolean) = {
    QueryParser.parse(query) match {

      // query parsed successfully, time to execute it!
      case Success(queryAst) =>
        Executor.execute(graphql.SchemaDefinition.RambutanSchema, queryAst, rambutanContext,
          operationName = operation,
          variables = variables getOrElse Json.obj(),
          deferredResolver = graphql.SchemaDefinition.Resolvers,
          exceptionHandler = exceptionHandler,
          queryReducers = List(
            QueryReducer.rejectMaxDepth[RambutanContext](15),
            QueryReducer.rejectComplexQueries[RambutanContext](4000, (_, _) => TooComplexQueryError)),
          middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
          .map(Ok(_))
          .recover {
            case error: QueryAnalysisError => BadRequest(error.resolveError)
            case error: ErrorWithResolver  => InternalServerError(error.resolveError)
          }

      // can't parse GraphQL query, return error
      case Failure(error: SyntaxError) =>
        Future.successful(BadRequest(Json.obj(
          "syntaxError" -> error.getMessage,
          "locations" -> Json.arr(Json.obj(
            "line" -> error.originalError.position.line,
            "column" -> error.originalError.position.column)))))

      case Failure(error) =>
        throw error
    }
  }

  def renderSchema = Action {
    Ok(SchemaRenderer.renderSchema(graphql.SchemaDefinition.RambutanSchema))
  }
}
