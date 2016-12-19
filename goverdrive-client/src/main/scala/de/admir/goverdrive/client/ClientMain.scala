package de.admir.goverdrive.client

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.FileMapping


object ClientMain extends App with StrictLogging {
    args match {
        case Array(localPath, remotePath) =>
            GoverdriveDb.insertFileMapping(FileMapping(None, None, localPath, remotePath)) match {
                case Right(fileMapping) => logger.info(s"Successfully added file mapping: $fileMapping")
                case Left(t) => logger.error("Could not add file mapping", t)
            }
        case Array("tearDown") => GoverdriveDb.tearDownDb()
        case _ => logger.error("Error while parsing input")
    }
}
