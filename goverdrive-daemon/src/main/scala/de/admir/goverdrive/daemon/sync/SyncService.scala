package de.admir.goverdrive.daemon.sync

import de.admir.goverdrive.scala.core.model.FileMapping

import scala.concurrent.Future

object SyncService {
    def syncToRemote(): Future[Seq[String Either FileMapping]] = {
        ???
    }
}
