package de.admir.goverdrive.client

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.FileMapping

import scala.util.{Failure, Success, Try}


object ClientMain extends App with StrictLogging {

    Try((args(0), args(1))) match {
        case Success((localPath, remotePath)) =>
            GoverdriveDb.insertFileMapping(FileMapping(None, None, localPath, remotePath)) match {
                case Right(fileMapping) => logger.info(s"Successfully added file mapping: $fileMapping")
                case Left(t) => logger.error("Could not add file mapping", t)
            }
        case Failure(t) => logger.error("Error while parsing input", t)
    }
}
