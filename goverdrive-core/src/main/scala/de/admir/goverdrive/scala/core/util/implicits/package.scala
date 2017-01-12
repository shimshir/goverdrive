package de.admir.goverdrive.scala.core.util

import java.io.{File => JFile}
import com.google.api.services.drive.model.{File => GFile}
import com.typesafe.scalalogging.LazyLogging
import de.admir.goverdrive.java.core.util.SystemUtils
import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}

import scala.util.{Failure, Success, Try}


package object implicits extends LazyLogging {

    private implicit class GFileImprovements(val file: GFile) {
        def isDirectory: Boolean = file.getMimeType == "application/vnd.google-apps.folder"

        def getAbsolutePath: String = {
            GoverdriveService.getAllFilesAndFolders match {
                case Left(driveError) =>
                    logger.error(s"I really have no idea how this happened, driveError: $driveError")
                    "i don't care, just fail it"
                case Right(allGoogleFiles) =>
                    def getPath(files: Seq[GFile], currentFile: GFile): Seq[GFile] = {
                        if (currentFile.getParents != null && !currentFile.getParents.isEmpty)
                            getPath(files, files.find(_.getId == currentFile.getParents.get(0)).get) :+ currentFile
                        else
                            Seq(currentFile)
                    }

                    getPath(allGoogleFiles, file)
                        .tail
                        .foldRight(SystemUtils.EMPTY_STRING)((file, acc) => s"/${file.getName}$acc")
            }
        }

        def listFiles(): Seq[GFile] = {
            GoverdriveService.getAllFilesAndFolders match {
                case Left(driveError) =>
                    logger.error(s"I really have no idea how this happened, driveError: $driveError")
                    Seq()
                case Right(allGoogleFiles) =>
                    allGoogleFiles.filter(gFile => {
                        if (gFile.getParents != null && !gFile.getParents.isEmpty)
                            gFile.getParents.contains(file.getId)
                        else
                            false
                    })
            }
        }
    }

    trait FileLike[T] {
        def fileTree(file: T): CoreFeedback Either Seq[T]

        def path(file: T): String
    }

    object FileLike {

        implicit object FileLikeJFile extends FileLike[JFile] {
            override def fileTree(file: JFile): CoreFeedback Either Seq[JFile] = {
                def innerFileTree(file: JFile): Seq[JFile] =
                    file :: (if (file.isDirectory) file.listFiles().toList.flatMap(innerFileTree) else Nil)

                Try(innerFileTree(file)) match {
                    case Failure(t) => Left(CoreFeedback(t))
                    case Success(files) => Right(files)
                }
            }

            override def path(file: JFile): String = file.getAbsolutePath
        }

        implicit object FileLikeGFile extends FileLike[GFile] {
            override def fileTree(file: GFile): CoreFeedback Either Seq[GFile] = {
                def innerFileTree(file: GFile): Seq[GFile] =
                    file :: (if (file.isDirectory) file.listFiles().toList.flatMap(innerFileTree) else Nil)

                Try(innerFileTree(file)) match {
                    case Failure(t) => Left(CoreFeedback(t))
                    case Success(files) => Right(files)
                }
            }

            override def path(file: GFile): String = file.getAbsolutePath
        }

    }

}
