package services

import models._
import models.RunConstants._

import silvousplay.imports._

import skuber._
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.json.batch.format._
import akka.Done
import akka.stream.scaladsl.{ Source, Sink }
import scala.concurrent.{ ExecutionContext, Future }
import javax.inject._
import java.nio.file.Paths

@Singleton
class RunService @Inject() (
  configuration: play.api.Configuration,
  logService:    LogService)(implicit materializer: akka.stream.Materializer, actorSystem: akka.actor.ActorSystem, ec: ExecutionContext) {

  val RambutanAnalysisNamespace = "rambutan-analysis"

  val isProd = configuration.get[Option[String]]("rambutan.environment").map(_ =?= "production").getOrElse(false)

  val k8s: skuber.api.client.RequestContext = {
    val kubeConfig = {
      if (isProd) {
        skuber.api.Configuration.inClusterConfig
      } else {
        skuber.api.Configuration.parseKubeconfigFile(
          Paths.get("apps/rambutan/conf/kube.conf"))
      }
    }.getOrElse {
      throw new Exception("failed to get kube config")
    }.setCurrentNamespace(RambutanAnalysisNamespace)

    k8sInit(kubeConfig)
  }

  private def createPodSpec(name: String, initContainers: List[ContainerDefinition], container: ContainerDefinition) = {
    val scriptVolumes = (container :: initContainers).flatMap(_.scriptVolume(name))

    Pod.Spec(
      initContainers = initContainers.map(_.toContainer(name)),
      containers = List(container.toContainer(name)),
      restartPolicy = RestartPolicy.Never,
      nodeSelector = Map(
        "cloud.google.com/gke-nodepool" -> "work-pool"),
      volumes = scriptVolumes ++ List(
        Volume(
          name = SERVICE_ACCOUNT,
          source = Volume.Secret(
            secretName = SERVICE_ACCOUNT,
            items = Option apply List(
              Volume.KeyToPath(
                GCS_SERVICE_ACCOUNT,
                GCS_SERVICE_ACCOUNT)))),
        Volume(
          name = OUTPUT_MOUNT,
          source = Volume.EmptyDir()),
        Volume(
          name = DATA_MOUNT,
          source = Volume.EmptyDir())))
  }

  def runPod(name: String, initializers: List[ContainerDefinition], main: ContainerDefinition)(implicit record: WorkRecord): Future[Unit] = {
    val configMaps = (main :: initializers).flatMap(_.scriptConfigMap(name))

    val podSpec = createPodSpec(
      name,
      initContainers = initializers,
      container = main)

    val pod = Pod(
      metadata = ObjectMeta(
        name = name,
        namespace = RambutanAnalysisNamespace,
        labels = Map("role" -> "analysis")),
      spec = Some(podSpec))

    for {
      _ <- ifNonEmpty(configMaps) {
        Future.sequence {
          configMaps.map(k8s create _)
        }
      }
      p <- k8s create pod
      _ <- logService.event(s"Created pod: ${name}")
      watchSource = k8s watchContinuously pod
      lastEvent <- watchSource.mapAsync(1) { watchEvent =>
        val maybeStatus = watchEvent._object.status
        val maybePhase = maybeStatus.flatMap(_.phase)
        for {
          _ <- withDefined(maybePhase) { phase =>
            logService.event(phase.toString)
          }
          containerStatuses = withDefined(maybeStatus) { status =>
            status.initContainerStatuses ++ status.containerStatuses
          }
          _ <- withDefined(maybePhase) { _ =>
            Future.sequence {
              containerStatuses.map { cs =>
                logService.event(s"${cs.name}: ${cs.lastState} => ${cs.state}")
              }
            }
          }
        } yield {
          watchEvent
        }
      }.takeWhile({ watchEvent =>
        val phase = watchEvent._object.status.flatMap(_.phase)
        phase =/= Some(Pod.Phase.Succeeded) && phase =/= Some(Pod.Phase.Failed)
      }, inclusive = true).runWith(Sink.last)
      // pull all logs and write to logService
      logRecord <- Source((initializers :+ main).map(_.name)).mapAsync(1) { containerName =>
        for {
          source <- k8s.getPodLogSource(name, Pod.LogQueryParams(containerName = Some(containerName)))
          _ <- logService.event(s"Logs for ${containerName}")
          _ <- source.map { byteString =>
            logService.event(s"[${containerName}] ${byteString.utf8String}")
          }.runWith(Sink.ignore)
        } yield {
          ()
        }
      }.runWith(Sink.ignore)
      succeeded = lastEvent._object.status.flatMap(_.phase) =?= Some(Pod.Phase.Succeeded)
      _ <- withFlag(succeeded) {
        k8s.delete[Pod](name)
      }
      _ <- withFlag(succeeded) {
        ifNonEmpty(configMaps) {
          Future.sequence {
            configMaps.map(cm => k8s.delete[ConfigMap](cm.name))
          }
        }
      }
      failed = lastEvent._object.status.flatMap(_.phase) =?= Some(Pod.Phase.Failed)
      _ = if (failed) {
        throw new Exception("k8s failure")
      }
    } yield {
      ()
    }
  }

}