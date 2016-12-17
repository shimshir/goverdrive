package de.admir.goverdrive.scala.core.model

import java.sql.Timestamp

case class FileMapping(pk: Option[Int] = None,
                       fileId: Option[String] = None,
                       localPath: String,
                       remotePath: String,
                       syncedAt: Option[Timestamp] = None)
