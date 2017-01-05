package de.admir.goverdrive.scala.core.model

import java.sql.Timestamp

import de.admir.goverdrive.scala.core.util.CaseClassBeautifier


case class LocalFolder(pk: Option[Int] = None, path: String, syncedAt: Option[Timestamp] = None) {
    override def toString: String = CaseClassBeautifier.nice(this)
}
