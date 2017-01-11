package de.admir.goverdrive.daemon

import de.admir.goverdrive.daemon.SyncResult._
import de.admir.goverdrive.daemon.feedback.DaemonFeedback
import de.admir.goverdrive.scala.core.model.{FileMapping, FolderMapping}

case class SyncResult(deletedLocalFiles: FileDeletes,
                      deletedRemoteFiles: FileDeletes,
                      deletedFoldersOnLocal: FolderDeletes,
                      deletedFoldersOnRemote: FolderDeletes,
                      syncedToRemoteFiles: FileSyncs,
                      syncedToLocalFiles: FileSyncs) {
}

object SyncResult {
    type FileSync = DaemonFeedback Either FileMapping
    type FileSyncs = Seq[FileSync]
    type FileDelete = FileSync
    type FileDeletes = FileSyncs

    type FolderSync = DaemonFeedback Either FolderMapping
    type FolderSyncs = Seq[FolderSync]
    type FolderDelete = FolderSync
    type FolderDeletes = FolderSyncs
}
