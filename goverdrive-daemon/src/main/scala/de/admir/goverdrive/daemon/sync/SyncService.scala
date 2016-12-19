package de.admir.goverdrive.daemon.sync

import java.io.File
import java.sql.Timestamp
import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.daemon.error.DaemonError
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.FileMapping
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object SyncService extends StrictLogging {

    def localExists(fileMapping: FileMapping): Boolean = {
        new File(fileMapping.localPath).exists()
    }

    def remoteExists(fileMapping: FileMapping): Boolean = {
        GoverdriveService.findFile(fileMapping.remotePath).isRight
    }

    def localTimestamp(fileMapping: FileMapping): Long = {
        fileMapping.syncedAt.map(timestamp => timestamp.getTime).getOrElse(-1)
    }

    def remoteTimestamp(fileMapping: FileMapping): Long = {
        GoverdriveService.findFile(fileMapping.remotePath) match {
            case Left(_) => -1
            case Right(file) => file.getModifiedTime.getValue
        }
    }

    def sync(): Future[(Seq[DaemonError Either FileMapping], Seq[DaemonError Either FileMapping])] = {
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

    def syncLocalToRemoteFuture(fileMappings: Seq[FileMapping]): Future[Seq[DaemonError Either FileMapping]] = {
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

    def syncRemoteToLocalFuture(fileMappings: Seq[FileMapping]): Future[Seq[DaemonError Either FileMapping]] = {
        ???
    }
}
