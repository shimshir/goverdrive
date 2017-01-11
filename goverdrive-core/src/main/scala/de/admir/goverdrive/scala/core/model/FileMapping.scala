package de.admir.goverdrive.scala.core.model

import java.sql.Timestamp
import de.admir.goverdrive.scala.core.util.CaseClassBeautifier


case class FileMapping(pk: Option[Int] = None,
                       fileId: Option[String] = None,
                       localPath: String,
                       remotePath: String,
                       syncedAt: Option[Timestamp] = None,
                       folderMappingPk: Option[Int] = None) {

    override def toString: String = CaseClassBeautifier.nice(this)
}
