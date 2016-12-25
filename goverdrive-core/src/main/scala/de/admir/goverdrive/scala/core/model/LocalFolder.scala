package de.admir.goverdrive.scala.core.model

import de.admir.goverdrive.scala.core.util.CaseClassBeautifier


case class LocalFolder(pk: Option[Int] = None, path: String) {
    override def toString: String = CaseClassBeautifier.nice(this)
}
