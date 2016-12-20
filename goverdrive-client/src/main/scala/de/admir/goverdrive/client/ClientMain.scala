package de.admir.goverdrive.client

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.scala.core.db.GoverdriveDb


object ClientMain extends App with StrictLogging {
    args match {
        case Array(localPath, remotePath) =>
            MappingProcessor.processMapping(localPath, remotePath) match {
                case Left(clientError) =>
                    logger.error(clientError.toString)
                case Right(mappings) =>
                    logger.info(mappings.toString())
            }
        case Array("tearDown") => GoverdriveDb.tearDownDb()
        case _ => logger.error("Error while parsing input")
    }
}
