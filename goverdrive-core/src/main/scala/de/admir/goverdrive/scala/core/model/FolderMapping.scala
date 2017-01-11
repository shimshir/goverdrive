package de.admir.goverdrive.scala.core.model

import java.sql.Timestamp

import de.admir.goverdrive.scala.core.util.CaseClassBeautifier


case class FolderMapping(pk: Option[Int] = None,
                         localPath: String,
                         remotePath: String,
                         syncedAt: Option[Timestamp] = None) {
    override def toString: String = CaseClassBeautifier.nice(this)
}
