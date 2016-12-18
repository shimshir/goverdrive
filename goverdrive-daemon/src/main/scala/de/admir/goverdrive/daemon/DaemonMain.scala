package de.admir.goverdrive.daemon

import java.sql.Timestamp

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.FileMapping
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


object DaemonMain extends App with StrictLogging {
    val workFuture: Future[Seq[String Either FileMapping]] = GoverdriveDb.getFileMappingsFuture
        .map(_.filter(_.fileId.isEmpty))
        .flatMap(fileMappings => {
            Future.sequence {
                fileMappings.map { fileMapping =>
                    GoverdriveService.createFile(fileMapping.localPath, fileMapping.remotePath) match {
                        case Right(driveFile) =>
                            GoverdriveDb.updateFileMappingFuture(
                                fileMapping.copy(
                                    fileId = Some(driveFile.getId),
                                    syncedAt = Some(new Timestamp(driveFile.getModifiedTime.getValue))
                                )
                            ).map(_.toRight(s"Could not update fileMapping: $fileMapping, in the DB"))
                        case Left(err) =>
                            logger.error(err.toString)
                            Future.successful(Left(err.toString))
                    }
                }
            }
        })
    val result = Await.result(workFuture, 15 minutes)
    logger.info(result.toString)
}
