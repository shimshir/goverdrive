package de.admir.goverdrive.daemon

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.java.core.{GoverdriveService, GoverdriveServiceImpl}


object DaemonMain extends App with StrictLogging {
    val goverdriveService: GoverdriveService = new GoverdriveServiceImpl

    while (true) {


        Thread.sleep(1000)
    }
}
