package services

import silvousplay.imports._
import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.ws.WSClient
import java.io.File
import org.apache.commons.io.FileUtils
import akka.stream.scaladsl.{ Sink, Source }
import scala.jdk.CollectionConverters._
import play.api.libs.json._
import java.nio.file.{ Paths, Files }

@Singleton
class CompilerService @Inject() (
  configuration:          play.api.Configuration,
  repoIndexDataService:   RepoIndexDataService,
  compilerQueueService:   CompilerQueueService,
  indexerQueueService:    IndexerQueueService,
  queueManagementService: QueueManagementService,
  fileService:            FileService,
  localFileService:       LocalFileService, // for dealing with compile directory
  socketService:          SocketService,
  logService:             LogService,
  wsClient:               WSClient)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val CompilerConcurrency = 1

  private val CompilerDirectory = configuration.get[String]("compiler.directory")
  private def compilerDirectory(index: RepoSHAIndex) = {
    s"${CompilerDirectory}/${index.esKey}"
  }

  def startRepoCompilation() = {
    // shutdown everything
    for {
      // get repos
      _ <- compilerQueueService.clearQueue()
      _ <- queueManagementService.runQueue[CompilerQueueItem](
        "compile-repo",
        concurrency = CompilerConcurrency,
        source = compilerQueueService.source) { item =>
          println("DEQUEUE", item)
          runCompile(item)
        } { item =>
          println("COMPLETE", item)
          Future.successful(())
        }
    } yield {
      ()
    }
  }

  def runCompile(item: CompilerQueueItem) = {
    for {
      compilerRecord <- {
        logService.getRecord(item.compilerRecordId).map(_.getOrElse(throw new Exception("invalid compiler record")))
      }
      _ <- logService.startRecord(compilerRecord)
      _ <- socketService.compilationProgress(item.orgId, item.compilerRecordId, 0)
      // clone into compiler directory
      index <- repoIndexDataService.getIndexId(item.indexId).map(_.getOrElse(throw new Exception("could not get index")))
      maybeRootIndex <- withDefined(index.rootIndexId) { rootId =>
        repoIndexDataService.getIndexId(rootId)
      }
      compileDirectory = compilerDirectory(index)
      _ <- logService.event(s"Cloning files for compile: ${compileDirectory}")(compilerRecord)
      _ <- cloneCompile(compileDirectory, index, maybeRootIndex)
      // find build.sbt. for now assume single build
      buildDirs <- findBuildDirs(compileDirectory)
      // send over build dirs
      _ <- Source(buildDirs).mapAsync(1) {
        case (compiler, buildRoot) => {
          val serverUrl = configuration.get[String](compiler.serverConfig)
          for {
            _ <- logService.event(s"Compiling for root: ${buildRoot}")(compilerRecord)
            _ <- wsClient.url(serverUrl + "/compile").post(Json.obj(
              "dir" -> buildRoot))
            // TODO: handle error
          } yield {
            ()
          }
        }
      }.runWith(Sink.ignore)
      _ <- logService.event("Finished compile. Copying analysis...")(compilerRecord)
      // pull out information
      analysisDirectory = index.analysisDirectory
      _ <- Source(buildDirs).mapAsync(1) {
        case (compiler, buildRoot) => {
          copyAnalysis(compiler, buildRoot, compileDirectory, analysisDirectory)(compilerRecord)
        }
      }.runWith(Sink.ignore)
      _ <- logService.event("Finished copying.")(compilerRecord)
      _ <- logService.finishRecord(compilerRecord)
      _ <- {
        // cleanup
        localFileService.deleteRecursiveAbsolute(compileDirectory)
      }
      _ <- socketService.compilationFinished(item.orgId, item.compilerRecordId, item.repo)
      _ <- indexerQueueService.enqueue(item.toIndexer)
    } yield {
      ()
    }
  }

  private def cloneCompile(compileDirectory: String, index: RepoSHAIndex, maybeRootIndex: Option[RepoSHAIndex]) = {
    val orgId = index.orgId
    val repoId = index.repoId

    val rootSource = withDefined(maybeRootIndex) { rootIndex =>
      // add partial compile here
      // annoying that we need to redownload here
      fileService.listDirectory(rootIndex.collectionsDirectory)
    }
    // overwrites
    val fileSource = {
      fileService.listDirectory(index.collectionsDirectory)
    }

    for {
      // we concat to overwrite
      // write to local only
      _ <- rootSource.concat(fileSource).mapAsync(1) {
        case (path, bytes) => {
          localFileService.writeAbsolute(s"${compileDirectory}/${path}", bytes)
        }
      }.runWith(Sink.ignore)
    } yield {
      ()
    }
  }

  // TODO: convert to using localFileService
  private def findBuildDirs(compileDirectory: String): Future[List[(CompilerType, String)]] = {
    Source(CompilerType.all).mapAsync(1) { compiler =>
      Future {
        FileUtils.listFiles(new File(compileDirectory), compiler.extensions.toArray, true).asScala.filter { file =>
          compiler.isValid(file.getName())
        }.map(f => (compiler, f.getParent()))
      }
    }.mapConcat(i => i.toList).runWith(Sinks.ListAccum)
  }

  private def copyAnalysis(compiler: CompilerType, buildRoot: String, compileDir: String, analysisDirectory: String)(implicit record: WorkRecord) = {
    compiler match {
      case CompilerType.MadVillain => copyMadvillainAnalysis(compiler, buildRoot, compileDir, analysisDirectory)
    }
  }

  private def copyMadvillainAnalysis(compiler: CompilerType, buildRoot: String, compileDir: String, analysisDirectory: String)(implicit record: WorkRecord) = {
    println("COPYING SCALA ANALYSIS", buildRoot, compileDir)
    for {
      // TODO: convert to using localFileService
      jsonFiles <- Future {
        // get the json files
        val base = new File(buildRoot + "/.bloop")
        base.listFiles.toList.filter { f =>
          f.isFile && f.getName().endsWith(".json")
        }
      }
      // TODO: convert to using localFileService
      directories <- Source(jsonFiles).mapAsync(1) { f =>
        Future {
          val source = scala.io.Source.fromFile(f) // local file this is fine
          val js = Json.parse(source.mkString)
          source.close
          val output = (js \ "project" \ "out").asOpt[String]
          output
        }
      }.mapConcat(i => i.toList).mapAsync(1) { output =>
        Future {
          val base = new File(output + "/bloop-internal-classes")
          println(s"Checking for generated files in ${base}")
          if (base.exists && base.isDirectory) {
            base.listFiles.toList map (_.getPath + "/META-INF/semanticdb")
          } else {
            Nil
          }
        }
      }.mapConcat(i => i).mapAsync(1) { semanticDir =>
        // realign directory into analysis directory
        val analysisDir = compiler.analysisType.path(analysisDirectory, "") // bad interface
        for {
          _ <- logService.event(s"Copying from ${semanticDir} to ${analysisDir}")
          // need to create custom
          fileSource = localFileService.listAbsolute(semanticDir)
          _ <- fileService.writeSource(fileSource, analysisDir)
        } yield {
          ()
        }
      }.runWith(Sinks.ListAccum)
    } yield {
      ()
    }
  }
}
