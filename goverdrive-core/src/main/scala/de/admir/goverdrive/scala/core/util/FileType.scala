package de.admir.goverdrive.scala.core.util

object FileType extends Enumeration {
    type FileType = Value
    val FILE = Value("file")
    val FOLDER = Value("folder")
}
