package services

import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.{ Source, Sink }

@Singleton
class IndexUpgradeService @Inject() (
  indexService: IndexService,
  dao:          dal.SharedDataAccessLayer,
  localDao:     dal.LocalDataAccessLayer)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  def deleteAllIndexesSync(): Future[Unit] = {
    for {
      allIndexes <- dao.RepoSHAIndexTable.all()
      _ <- Source(allIndexes).mapAsync(1) { index =>
        for {
          _ <- indexService.deleteKey(index)
        } yield {
          ()
        }
      }.runWith(Sink.ignore)
      _ <- dao.RepoSHAIndexTable.deleteAll()
      _ <- localDao.LocalRepoConfigTable.deleteAll()
    } yield {
      ()
    }
  }

}

