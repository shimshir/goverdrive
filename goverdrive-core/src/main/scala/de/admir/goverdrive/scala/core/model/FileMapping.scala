package de.admir.goverdrive.scala.core.model

import java.sql.Timestamp

import de.admir.goverdrive.scala.core.util.CaseClassBeautifier


case class FileMapping(pk: Option[Int] = None,
                       fileId: Option[String] = None,
                       localPath: String,
                       localTimestamp: Option[Timestamp] = None,
                       remotePath: String,
                       remoteTimestamp: Option[Timestamp] = None,
                       syncedAt: Option[Timestamp] = None) {

    override def toString: String = CaseClassBeautifier.nice(this)
}
