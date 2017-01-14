package de.admir.goverdrive.scala.core.typeclasses

import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import de.admir.goverdrive.scala.core.model.{FileMapping, FolderMapping}

trait FileLike[T] {
    def fileTree(file: T, onlyFiles: Boolean = false): Seq[CoreFeedback Either T]

    def path(file: T): String

    def isDirectory(file: T): Boolean

    def folder(folderMapping: FolderMapping): CoreFeedback Either T

    def origin: String

    def relativeFolderFileMapping(file: T, folderMapping: FolderMapping): FileMapping

    protected def listFiles(file: T): Seq[T]
}
