package de.admir.goverdrive.daemon

import java.io.{File, FileOutputStream}
import java.sql.Timestamp

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.daemon.SyncResult.{FileSyncs, _}
import de.admir.goverdrive.daemon.feedback.DaemonFeedback
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.{FileMapping, FolderMapping}
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}
import net.java.truecommons.shed.ResourceLoan._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import de.admir.goverdrive.scala.core.MappingUtils._

import scala.concurrent.duration._
import scala.language.postfixOps
import scalax.file.Path


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

    def deleteDeletedSyncedFolderMappings(folderIsDeletedPredicate: FolderMapping => Boolean,
                                          deleteFolderAction: FolderMapping => DaemonFeedback Either FolderMapping): Future[FolderDeletes] = {
        GoverdriveDb.getFolderMappingsFuture flatMap { folderMappings =>
            val deletedSyncedFolderMappings: Seq[FolderMapping] = folderMappings.filter(_.syncedAt.isDefined).filter(folderIsDeletedPredicate)

            Future.sequence {
                deletedSyncedFolderMappings map { folderMapping =>
                    deleteFolderAction(folderMapping) match {
                        case Left(daemonFeedback) =>
                            Future.successful(Left(daemonFeedback))
                        case Right(_) =>
                            val folderMappingDeleteFuture: Future[FolderDelete] = GoverdriveDb.deleteFolderMappingFuture(folderMapping.pk.get) map {
                                case 0 =>
                                    val errorMessage = s"Failed to delete folderMapping: $folderMapping"
                                    logger.error(errorMessage)
                                    Left(DaemonFeedback(errorMessage))
                                case 1 =>
                                    Right(folderMapping)
                                case deletedRows =>
                                    logger.warn(s"Multiple rows deleted ($deletedRows) for one folderMapping: $folderMapping")
                                    Right(folderMapping)
                            }
                            folderMappingDeleteFuture
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

    def syncAddedFilesToFolders(): Future[FileSyncs] = {
        ???
    }

    def sync: Future[SyncResult] = {
        GoverdriveDb.getFileMappingsFuture.flatMap(fileMappings => {
            val syncedFileMappings = fileMappings.filter(_.fileId.isDefined)

            /**
              * Check for locally deleted files, if (synced) { delete them remotely and remove fileMapping entry }
              **/
            val deletedLocalFilesFuture: Future[FileDeletes] = deleteDeletedSyncedFiles(
                syncedFileMappings.filterNot(localExists).filter(remoteExists),
                deleteFileMappingRemotely
            )

            /**
              * Check for remotely deleted files, if (synced) { delete them locally and remove fileMapping entry }
              */
            val deletedRemoteFilesFuture: Future[FileDeletes] = deleteDeletedSyncedFiles(
                syncedFileMappings.filterNot(remoteExists).filter(localExists),
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
              * Check if folderMappings were deleted locally, if (synced) { delete them remotely and remove folderMapping entry }
              */
            val deletedSyncedLocalFolderMappingsFuture: Future[FolderDeletes] = deleteDeletedSyncedFolderMappings(
                folderMapping => !localExists(folderMapping.localPath) && remoteExists(folderMapping.remotePath),

                /**
                  * Get all fileMappings for the folderMapping, delete folder and its files remotely, delete folderMapping entry and its fileMapping entries from the DB
                  */
                folderMapping =>
                    GoverdriveService.deleteFile(folderMapping.remotePath) match {
                        case Left(driveError) =>
                            val errorMsg = s"Error while remotely deleting folder, folderMapping: $folderMapping, driveError: $driveError"
                            logger.error(errorMsg)
                            Left(DaemonFeedback(errorMsg, driveError))
                        case _ => Right(folderMapping)
                    }
            )

            /**
              * Check if folderMappings were deleted remotely, if (synced) { delete them locally and remove folderMapping entry }
              */
            val deletedSyncedRemoteFolderMappingsFuture: Future[FolderDeletes] = deleteDeletedSyncedFolderMappings(
                folderMapping => !remoteExists(folderMapping.remotePath) && localExists(folderMapping.localPath),

                /**
                  * Get all fileMappings for the folderMapping, delete folder and its files locally, delete folderMapping entry from the DB
                  */
                folderMapping =>
                    Try(Path.fromString(folderMapping.localPath).deleteRecursively(continueOnFailure = false)) match {
                        case Failure(t) =>
                            val errorMsg = s"Error while locally deleting folder, folderMapping: $folderMapping"
                            logger.error(errorMsg, t)
                            Left(DaemonFeedback(errorMsg, t))
                            // TODO: Should be handled in some way, at least log on unexpected return
                        case Success((deletedCount, remainingCount)) =>
                            Right(folderMapping)
                    }
            )

            // TODO: Check for files inside folderMappings that were added locally and add a fileMapping entry for them
            val newlyAddedFilesToRemoteFoldersFuture: Future[FileSyncs] = syncAddedFilesToFolders()

            // TODO: Check if files inside folderMappings were added remotely and add a fileMapping entry for them
            val newlyAddedFilesToLocalFoldersFuture: Future[FileSyncs] = syncAddedFilesToFolders()

            val syncedToRemoteFilesFuture: Future[FileSyncs] = syncLocalToRemoteFuture(filterLocalToRemoteSyncables(fileMappings))

            /**
              * Get the updated fileMappings from the DB again to prevent syncing from remote to local right after syncing from local to remote
              */
            val syncedToLocalFilesFuture: Future[FileSyncs] = GoverdriveDb.getFileMappingsFuture flatMap { updatedFileMappings =>
                syncRemoteToLocalFuture(filterRemoteToLocalSyncables(updatedFileMappings))
            }

            for {
                deletedLocalFiles <- deletedLocalFilesFuture
                deletedRemoteFiles <- deletedRemoteFilesFuture
                deletedSyncedLocalFolderMappings <- deletedSyncedLocalFolderMappingsFuture
                deletedSyncedRemoteFolderMappings <- deletedSyncedRemoteFolderMappingsFuture
                syncedToRemoteFiles <- syncedToRemoteFilesFuture
                syncedToLocalFiles <- syncedToLocalFilesFuture

            } yield SyncResult(
                deletedLocalFiles,
                deletedRemoteFiles,
                deletedSyncedLocalFolderMappings,
                deletedSyncedRemoteFolderMappings,
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

    def updateFileMappingAndOptionallyFolderMapping(fileMapping: FileMapping, driveFileId: String): Future[Either[DaemonFeedback, FileMapping]] = {
        val timestamp = new Timestamp(System.currentTimeMillis)
        val folderMappingUpdateFuture: Future[Option[Option[FolderMapping]]] = fileMapping.folderMappingPk.map(folderMappingPk =>
            GoverdriveDb.getFolderMappingFuture(folderMappingPk).flatMap {
                case Some(folderMapping) => GoverdriveDb.updateFolderMappingFuture(folderMapping.copy(syncedAt = Some(timestamp)))
                case None => Future.successful(None)
            }
        ) match {
            case Some(f) => f.map(Some(_))
            case None => Future.successful(None)
        }

        val fileMappingUpdateFuture: Future[Option[FileMapping]] = GoverdriveDb.updateFileMappingFuture(fileMapping.copy(fileId = Some(driveFileId), syncedAt = Some(timestamp)))

        val updatesResultFuture: Future[(Option[Option[FolderMapping]], Option[FileMapping])] = for {
            folderMappingUpdate <- folderMappingUpdateFuture
            fileMappingUpdate <- fileMappingUpdateFuture
        } yield (folderMappingUpdate, fileMappingUpdate)

        updatesResultFuture.map {
            case (None, Some(updatedFileMapping)) =>
                logger.info(s"Successfully synced and updated fileMapping: $updatedFileMapping")
                Right(updatedFileMapping)
            case (Some(None), Some(updatedFileMapping)) =>
                logger.info(s"Successfully synced and updated fileMapping: $updatedFileMapping")
                logger.warn(s"Could not find folderMapping entry for fileMapping: $fileMapping")
                Right(updatedFileMapping)
            case (Some(Some(updatedFolderMapping)), Some(updatedFileMapping)) =>
                logger.info(s"Successfully synced and updated fileMapping: $updatedFileMapping and updated folderMapping: $updatedFolderMapping")
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
                        updateFileMappingAndOptionallyFolderMapping(fileMapping, driveFile.getId)
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
                                                    updateFileMappingAndOptionallyFolderMapping(fileMapping, driveFile.getId)
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
