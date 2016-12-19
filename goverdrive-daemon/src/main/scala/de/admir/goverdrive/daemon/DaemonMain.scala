package de.admir.goverdrive.daemon

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.daemon.sync.SyncService
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps


object DaemonMain extends App with StrictLogging {
    Await.result(SyncService.sync, 1 minute)
}
