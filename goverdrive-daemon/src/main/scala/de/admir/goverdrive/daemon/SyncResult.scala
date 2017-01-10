package de.admir.goverdrive.daemon

import de.admir.goverdrive.daemon.SyncResult.{FileSyncs, FolderSyncs}
import de.admir.goverdrive.daemon.feedback.DaemonFeedback
import de.admir.goverdrive.scala.core.model.{FileMapping, LocalFolder}

case class SyncResult(deletedLocalFiles: FileSyncs,
                      deletedRemoteFiles: FileSyncs,
                      deletedFoldersOnLocal: FolderSyncs,
                      deletedFoldersOnRemote: FolderSyncs,
                      syncedToRemoteFiles: FileSyncs,
                      syncedToLocalFiles: FileSyncs) {
}

object SyncResult {
    type FileSync = DaemonFeedback Either FileMapping
    type FileSyncs = Seq[FileSync]
    type FileDelete = FileSync
    type FileDeletes = FileSyncs

    type FolderSync = DaemonFeedback Either LocalFolder
    type FolderSyncs = Seq[FolderSync]
    type FolderDelete = FolderSync
    type FolderDeletes = FolderSyncs
}
