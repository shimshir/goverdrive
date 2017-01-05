package de.admir.goverdrive.daemon

import java.io.{File, FileOutputStream}
import java.sql.Timestamp

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.daemon.feedback.DaemonFeedback
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.{FileMapping, LocalFolder}
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}
import net.java.truecommons.shed.ResourceLoan._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import de.admir.goverdrive.scala.core.MappingService._

import scala.concurrent.duration._
import scala.language.postfixOps


object SyncService extends StrictLogging {

    val outOfSyncThreshold: Long = 5.seconds.toMillis

    def sync: Future[(Seq[DaemonFeedback Either FileMapping], Seq[DaemonFeedback Either FileMapping])] = {

        /*
        // TODO: Check for locally deleted files, if (synced) { delete them remotely and remove fileMapping entry }
        checkForDeletedLocalFiles()

        // TODO: Check for locally deleted folders, if (synced) { delete them remotely, remove localFolder entry }
        checkForDeletedLocalFolders()

        // TODO: Check for remotely deleted files, if (synced) { delete them locally and remove fileMapping entry }
        checkForDeletedRemoteFiles()

        // TODO: Check for remotely deleted folders, if (synced) {delete them locally and remove remoteFolder entry }
        // might not be needed
        // checkForDeletedRemoteFolders()

        // TODO: Check if files inside localFolders were added or removed locally and sync changes to remote
        checkForAddedOrRemovedLocalFilesInsideFolders()

        // TODO: Check if files inside localFolders were added or removed remotely and sync changes to local
        checkForAddedOrRemovedRemoteFilesInsideFolders()
        */

        GoverdriveDb.getFileMappingsFuture.flatMap(fileMappings => {
            val syncedFileMappings = fileMappings.filter(_.fileId.isDefined)

            val localFileMappingsToDelete = syncedFileMappings.filter(!remoteExists(_))
            val remoteFileMappingsToDelete = syncedFileMappings.filter(!localExists(_))

            val localToRemoteSyncables = filterLocalToRemoteSyncables(fileMappings)
            val remoteToLocalSyncables = filterRemoteToLocalSyncables(fileMappings)
            for {
                syncLocalToRemoteResult <- syncLocalToRemoteFuture(localToRemoteSyncables)
                syncRemoteToLocalResult <- syncRemoteToLocalFuture(remoteToLocalSyncables)
            } yield (syncLocalToRemoteResult, syncRemoteToLocalResult)
        })
    }

    def filterLocalToRemoteSyncables(fileMappings: Seq[FileMapping]): Seq[FileMapping] = {
        fileMappings.filter(fileMapping =>
            (!remoteExists(fileMapping) && localExists(fileMapping)
                ||
                (remoteExists(fileMapping) && localExists(fileMapping) && (localTimestamp(fileMapping) - fileMapping.syncedAt.map(_.getTime).getOrElse(0L) > outOfSyncThreshold)))
        )
    }

    def filterRemoteToLocalSyncables(fileMappings: Seq[FileMapping]): Seq[FileMapping] = {
        fileMappings.filter(fileMapping =>
            (remoteExists(fileMapping) && !localExists(fileMapping)
                ||
                (remoteExists(fileMapping) && localExists(fileMapping) && (remoteTimestamp(fileMapping) - fileMapping.syncedAt.map(_.getTime).getOrElse(0L) > outOfSyncThreshold)))
        )
    }

    def updateFileMappingAndOptionallyLocalFolder(fileMapping: FileMapping, driveFileId: String): Future[Either[DaemonFeedback, FileMapping]] = {
        val timestamp = new Timestamp(System.currentTimeMillis)
        val localFolderUpdateFuture: Future[Option[Option[LocalFolder]]] = fileMapping.localFolderPk.map(localFolderPk =>
            GoverdriveDb.getLocalFolderFuture(localFolderPk).flatMap {
                case Some(localFolder) => GoverdriveDb.updateLocalFolderFuture(localFolder.copy(syncedAt = Some(timestamp)))
                case None => Future.successful(None)
            }
        ) match {
            case Some(f) => f.map(Some(_))
            case None => Future.successful(None)
        }

        val fileMappingUpdateFuture: Future[Option[FileMapping]] = GoverdriveDb.updateFileMappingFuture(fileMapping.copy(fileId = Some(driveFileId), syncedAt = Some(timestamp)))

        val updatesResultFuture: Future[(Option[Option[LocalFolder]], Option[FileMapping])] = for {
            localFolderUpdate <- localFolderUpdateFuture
            fileMappingUpdate <- fileMappingUpdateFuture
        } yield (localFolderUpdate, fileMappingUpdate)

        updatesResultFuture.map {
            case (None, Some(updatedFileMapping)) =>
                val successMessage = s"Successfully synced and updated fileMapping: $updatedFileMapping"
                logger.info(successMessage)
                Right(updatedFileMapping)
            case (Some(None), Some(updatedFileMapping)) =>
                logger.info(s"Successfully synced and updated fileMapping: $updatedFileMapping")
                logger.warn(s"Could not find localFolder entry for fileMapping: $fileMapping")
                Right(updatedFileMapping)
            case (Some(Some(updatedLocalFolder)), Some(updatedFileMapping)) =>
                logger.info(s"Successfully synced and updated fileMapping: $updatedFileMapping and updated localFolder: $updatedLocalFolder")
                Right(updatedFileMapping)
            case _ =>
                val errorMessage = s"Could upload data but not update fileMapping: $fileMapping"
                logger.error(errorMessage)
                Left(DaemonFeedback(errorMessage))
        }
    }

    def syncLocalToRemoteFuture(fileMappings: Seq[FileMapping]): Future[Seq[DaemonFeedback Either FileMapping]] = {
        Future.sequence {
            fileMappings.map { fileMapping =>
                GoverdriveService.createFile(fileMapping.localPath, fileMapping.remotePath) match {
                    case Left(error) =>
                        logger.error(error.toString)
                        Future.successful(Left(DaemonFeedback(s"Error while syncing file to remote, fileMapping: $fileMapping", error)))
                    case Right(driveFile) =>
                        updateFileMappingAndOptionallyLocalFolder(fileMapping, driveFile.getId)
                }
            }
        }
    }

    def syncRemoteToLocalFuture(fileMappings: Seq[FileMapping]): Future[Seq[DaemonFeedback Either FileMapping]] = {
        Future.sequence {
            fileMappings.map(fileMapping => {
                GoverdriveService.getFile(fileMapping.remotePath) match {
                    case Left(error) =>
                        Future.successful(Left(DaemonFeedback(s"Could not find remote file: ${fileMapping.remotePath}", error)))
                    case Right(driveFile) =>
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
                                                case Success(_) =>
                                                    updateFileMappingAndOptionallyLocalFolder(fileMapping, driveFile.getId)
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
