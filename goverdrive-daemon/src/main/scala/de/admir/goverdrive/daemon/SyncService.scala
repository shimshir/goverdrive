package de.admir.goverdrive.daemon

import java.io.{File, FileOutputStream}
import java.sql.Timestamp

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.daemon.feedback.DaemonFeedback
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.FileMapping
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}
import net.java.truecommons.shed.ResourceLoan._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import de.admir.goverdrive.scala.core.MappingService._


object SyncService extends StrictLogging {

    def sync: Future[(Seq[DaemonFeedback Either FileMapping], Seq[DaemonFeedback Either FileMapping])] = {
        GoverdriveDb.getFileMappingsFuture.flatMap(fileMappings => {
            val localToRemoteSyncables = filterLocalToRemoteSyncables(fileMappings)
            val remoteToLocalSyncables = filterRemoteToLocalSyncables(fileMappings)
            for {
                syncLocalToRemoteResult <- syncLocalToRemoteFuture(localToRemoteSyncables)
                syncRemoteToLocalResult <- syncRemoteToLocalFuture(remoteToLocalSyncables)
            } yield (syncLocalToRemoteResult, syncRemoteToLocalResult)
        })
    }

    def filterLocalToRemoteSyncables(fileMappings: Seq[FileMapping]): Seq[FileMapping] = {
        fileMappings.filter(fileMapping => {
            if (
                !remoteExists(fileMapping) && localExists(fileMapping)
                    ||
                    (remoteExists(fileMapping) && localExists(fileMapping) && remoteTimestamp(fileMapping) < localTimestamp(fileMapping))
            ) true
            else false
        })
    }

    def filterRemoteToLocalSyncables(fileMappings: Seq[FileMapping]): Seq[FileMapping] = {
        fileMappings.filter(fileMapping => {
            if ((remoteExists(fileMapping) && localExists(fileMapping) && remoteTimestamp(fileMapping) > localTimestamp(fileMapping))
                ||
                remoteExists(fileMapping) && !localExists(fileMapping)
            ) true
            else false
        })
    }

    def syncLocalToRemoteFuture(fileMappings: Seq[FileMapping]): Future[Seq[DaemonFeedback Either FileMapping]] = {
        Future.sequence {
            fileMappings.map { fileMapping =>
                GoverdriveService.createFile(fileMapping.localPath, fileMapping.remotePath) match {
                    case Left(error) =>
                        logger.error(error.toString)
                        Future.successful(Left(DaemonFeedback(s"Error while syncing file to remote, fileMapping: $fileMapping", error)))
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
                                val errorMessage = s"Could upload data but not update fileMapping: $fileMapping"
                                logger.error(errorMessage)
                                Left(DaemonFeedback(errorMessage))
                        }
                }
            }
        }
    }

    def syncRemoteToLocalFuture(fileMappings: Seq[FileMapping]): Future[Seq[DaemonFeedback Either FileMapping]] = {
        Future.sequence {
            fileMappings.map(fileMapping => {
                GoverdriveService.findFile(fileMapping.remotePath) match {
                    case Left(error) =>
                        Future.successful(Left(DaemonFeedback(s"Could not find remote file: ${fileMapping.remotePath}", error)))
                    case Right(file) =>
                        GoverdriveService.getFileStream(fileMapping.remotePath) match {
                            case Left(error) =>
                                Future.successful(Left(DaemonFeedback(s"Could not download remote file: ${fileMapping.remotePath}", error)))
                            case Right(outputStream) =>
                                loan(outputStream) to { os =>
                                    Try {
                                        val localFileParentFolder = new File(new File(fileMapping.localPath).getParent)
                                        if (!localFileParentFolder.exists()) {
                                            localFileParentFolder.mkdirs()
                                        }
                                        loan(new FileOutputStream(fileMapping.localPath)) to { fileOutputStream =>
                                            Try(os.writeTo(fileOutputStream)) match {
                                                case Failure(t) =>
                                                    Future.successful(Left(DaemonFeedback(t)))
                                                case Success(_) => GoverdriveDb
                                                    .updateFileMappingFuture(fileMapping.copy(syncedAt = Some(new Timestamp(file.getModifiedTime.getValue))))
                                                    .map {
                                                        case Some(updatedFileMapping) =>
                                                            val successMessage = s"Successfully synced and updated fileMapping: $updatedFileMapping"
                                                            logger.info(successMessage)
                                                            Right(updatedFileMapping)
                                                        case _ =>
                                                            val errorMessage = s"Could download data but not update fileMapping: $fileMapping"
                                                            logger.error(errorMessage)
                                                            Left(DaemonFeedback(errorMessage))
                                                    }
                                            }
                                        }
                                    } match {
                                        case Failure(t) =>
                                            val errorMessage = s"Error while opening fileOutputStream to: ${fileMapping.localPath}"
                                            logger.error(errorMessage, t)
                                            Future.successful(Left(DaemonFeedback(errorMessage, t)))
                                        case Success(x) => x
                                    }
                                }
                        }
                }
            })
        }
    }
}
