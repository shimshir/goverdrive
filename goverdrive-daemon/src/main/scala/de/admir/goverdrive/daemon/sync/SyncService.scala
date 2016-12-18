package de.admir.goverdrive.daemon.sync

import java.sql.Timestamp

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.daemon.error.DaemonError
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.FileMapping
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SyncService extends StrictLogging {
    def syncToRemoteFuture: Future[Seq[DaemonError Either FileMapping]] = {
        GoverdriveDb.getFileMappingsFuture
            .map(_.filter(_.fileId.isEmpty))
            .flatMap {
                case Seq() =>
                    val infoMessage = "No fileMappings without fileId in DB"
                    logger.info(infoMessage)
                    Future.successful(Seq(Left(DaemonError(infoMessage))))
                case fileMappings =>
                    Future.sequence {
                        fileMappings.map { fileMapping =>
                            GoverdriveService.createFile(fileMapping.localPath, fileMapping.remotePath) match {
                                case Right(driveFile) =>
                                    GoverdriveDb.updateFileMappingFuture(
                                        fileMapping.copy(
                                            fileId = Some(driveFile.getId),
                                            syncedAt = Some(new Timestamp(driveFile.getModifiedTime.getValue))
                                        )
                                    ).map {
                                        case Some(updatedFileMapping) =>
                                            val successMessage = s"Successfully synced and updated fileMapping: $updatedFileMapping"
                                            logger.info(successMessage)
                                            Right(updatedFileMapping)
                                        case _ =>
                                            val errorMessage = s"Could sync but not update fileMapping: $fileMapping"
                                            logger.error(errorMessage)
                                            Left(DaemonError(errorMessage))
                                    }
                                case Left(error) =>
                                    logger.error(error.toString)
                                    Future.successful(Left(DaemonError(s"Error while syncing file to remote, fileMapping: $fileMapping", error)))
                            }
                        }
                    }
            }
    }
}
