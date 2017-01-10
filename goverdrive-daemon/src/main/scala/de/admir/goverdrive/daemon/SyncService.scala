package de.admir.goverdrive.daemon

import java.io.{File, FileOutputStream}
import java.sql.Timestamp

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.daemon.SyncResult._
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

    def deleteDeletedSyncedFiles(deletedSyncedFileMappingsToDelete: Seq[FileMapping],
                                 deleteFileAction: FileMapping => DaemonFeedback Either FileMapping): Future[FileDeletes] = {
        Future.sequence {
            deletedSyncedFileMappingsToDelete map { fileMapping =>
                deleteFileAction(fileMapping) match {
                    case Left(daemonFeedback) =>
                        Future.successful(Left(daemonFeedback))
                    case Right(_) =>
                        GoverdriveDb.deleteFileMappingFuture(fileMapping.pk) map {
                            case 0 =>
                                val errorMessage = s"Failed to delete fileMapping: $fileMapping"
                                logger.error(errorMessage)
                                Left(DaemonFeedback(errorMessage))
                            case 1 =>
                                Right(fileMapping)
                            case deletedRows =>
                                logger.warn(s"Multiple rows deleted ($deletedRows) for one fileMapping: $fileMapping")
                                Right(fileMapping)
                        }
                }
            }
        }
    }

    def deleteDeletedSyncedLocalFolders(folderIsDeletedPredicate: LocalFolder => Boolean,
                                        deleteFolderAction: LocalFolder => DaemonFeedback Either LocalFolder): Future[FolderDeletes] = {
        GoverdriveDb.getLocalFoldersFuture flatMap { localFolders =>
            val deletedSyncedLocalFolders: Seq[LocalFolder] = localFolders.filter(_.syncedAt.isDefined).filter(folderIsDeletedPredicate)

            Future.sequence {
                deletedSyncedLocalFolders map { localFolder =>
                    deleteFolderAction(localFolder) match {
                        case Left(daemonFeedback) =>
                            Future.successful(Left(daemonFeedback))
                        case Right(_) =>
                            val localFolderDeleteFuture: Future[FolderDelete] = GoverdriveDb.deleteLocalFolderFuture(localFolder.pk.get) map {
                                case Left(coreFeedback) =>
                                    val errorMessage = s"Error while trying to delete localFolder: $localFolder"
                                    logger.error(errorMessage)
                                    Left(DaemonFeedback(errorMessage, coreFeedback))
                                case Right(0) =>
                                    Right(localFolder)
                                case Right(deletedRows) =>
                                    logger.warn(s"Multiple rows deleted ($deletedRows) for one localFolder: $localFolder")
                                    Right(localFolder)
                            }
                            localFolderDeleteFuture
                    }
                }
            }
        }
    }

    def deleteFileMappingRemotely(fileMapping: FileMapping): DaemonFeedback Either FileMapping = {
        GoverdriveService.deleteFile(fileMapping.remotePath) match {
            case Left(driveError) =>
                val errorMsg = s"Error while remotely deleting file, fileMapping: $fileMapping, driveError: $driveError"
                logger.error(errorMsg)
                Left(DaemonFeedback(errorMsg, driveError))
            case _ => Right(fileMapping)
        }
    }

    def sync: Future[SyncResult] = {
        GoverdriveDb.getFileMappingsFuture.flatMap(fileMappings => {
            val syncedFileMappings = fileMappings.filter(_.fileId.isDefined)

            /**
              * Check for locally deleted files, if (synced) { delete them remotely and remove fileMapping entry }
              **/
            val deletedLocalFilesFuture: Future[FileDeletes] = deleteDeletedSyncedFiles(
                syncedFileMappings.filterNot(localExists),
                deleteFileMappingRemotely
            )

            /**
              * Check for remotely deleted files, if (synced) { delete them locally and remove fileMapping entry }
              */
            val deletedRemoteFilesFuture: Future[FileDeletes] = deleteDeletedSyncedFiles(
                syncedFileMappings.filterNot(remoteExists),
                fileMapping =>
                    if (new File(fileMapping.localPath).delete())
                        Right(fileMapping)
                    else {
                        val errorMsg = s"Error while locally deleting file, fileMapping: $fileMapping"
                        logger.error(errorMsg)
                        Left(DaemonFeedback(errorMsg))
                    }
            )

            /**
              * Check if localFolders were deleted locally, if (synced) { delete them remotely and remove localFolder entry }
              */
            val deletedSyncedLocalLocalFoldersFuture: Future[FolderDeletes] = deleteDeletedSyncedLocalFolders(
                localFolder => !localExists(localFolder.path),
                /**
                  * Get all fileMappings for the localFolder, delete folder and its files remotely, delete localFolder entry and its fileMapping entries from the DB
                  */
                localFolder => {
                    GoverdriveService.deleteFile(localFolder.path) match {
                        case Left(driveError) =>
                            val errorMsg = s"Error while remotely deleting folder, localFolder: $localFolder, driveError: $driveError"
                            logger.error(errorMsg)
                            Left(DaemonFeedback(errorMsg, driveError))
                        case _ => Right(localFolder)
                    }
                }
            )

            /**
              * Check if localFolders were deleted remotely, if (synced) { delete them locally and remove localFolder entry }
              */
            val deletedSyncedRemoteLocalFoldersFuture: Future[FolderDeletes] = deleteDeletedSyncedLocalFolders(
                // FIXME: This is wrong, remote folders don't have the same path as local folders
                localFolder => !remoteExists(localFolder.path),
                localFolder =>
                    /**
                      * Get all fileMappings for the localFolder, delete folder and its files locally, delete localFolder entry from the DB
                      */
                    // TODO: Use another way to delete a directory
                    if (new File(localFolder.path).delete())
                        Right(localFolder)
                    else {
                        val errorMsg = s"Error while locally deleting folder, localFolder: $localFolder"
                        logger.error(errorMsg)
                        Left(DaemonFeedback(errorMsg))
                    }
            )

            // TODO: Check for files inside localFolders that were added locally and add a fileMapping entry for them
            // checkForAddedOrRemovedLocalFilesInsideFolders()

            // TODO: Check if files inside localFolders were added remotely and add a fileMapping entry for them
            // checkForAddedOrRemovedRemoteFilesInsideFolders()

            val syncedToRemoteFilesFuture: Future[FileSyncs] = syncLocalToRemoteFuture(filterLocalToRemoteSyncables(fileMappings))
            val syncedToLocalFilesFuture: Future[FileSyncs] = syncRemoteToLocalFuture(filterRemoteToLocalSyncables(fileMappings))

            for {
                deletedLocalFiles <- deletedLocalFilesFuture
                deletedRemoteFiles <- deletedRemoteFilesFuture
                deletedSyncedLocalLocalFolders <- deletedSyncedLocalLocalFoldersFuture
                deletedSyncedRemoteLocalFolders <- deletedSyncedRemoteLocalFoldersFuture
                syncedToRemoteFiles <- syncedToRemoteFilesFuture
                syncedToLocalFiles <- syncedToLocalFilesFuture

            } yield SyncResult(
                deletedLocalFiles,
                deletedRemoteFiles,
                deletedSyncedLocalLocalFolders,
                deletedSyncedRemoteLocalFolders,
                syncedToRemoteFiles,
                syncedToLocalFiles
            )
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

    def syncLocalToRemoteFuture(fileMappings: Seq[FileMapping]): Future[FileSyncs] = {
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

    def syncRemoteToLocalFuture(fileMappings: Seq[FileMapping]): Future[FileSyncs] = {
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
