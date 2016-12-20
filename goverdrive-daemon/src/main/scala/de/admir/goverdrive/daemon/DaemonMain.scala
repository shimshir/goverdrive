package de.admir.goverdrive.daemon

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps


object DaemonMain extends App with StrictLogging {
    val result = Await.result(SyncService.sync, 10 minutes)
    println(result)
}
