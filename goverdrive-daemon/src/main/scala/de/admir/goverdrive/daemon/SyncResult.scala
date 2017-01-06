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
    type FileSyncs = Seq[DaemonFeedback Either FileMapping]
    type FileDeletes = FileSyncs
    type FolderSyncs = Seq[DaemonFeedback Either LocalFolder]
    type FolderDeletes = FolderSyncs
}
