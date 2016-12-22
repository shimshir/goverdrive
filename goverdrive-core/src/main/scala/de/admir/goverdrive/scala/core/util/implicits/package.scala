package de.admir.goverdrive.scala.core.util

import java.sql.Timestamp

import com.google.api.services.drive.model.{File => GFile}
import com.typesafe.scalalogging.LazyLogging
import de.admir.goverdrive.java.core.util.SystemUtils
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}


package object implicits extends LazyLogging {

    implicit class GFileImprovements(val file: GFile) {
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

    implicit class TimestampOrdering(a: Timestamp) {
        def compare(b: Timestamp): Int = a.getTime compare b.getTime
    }

    /*
    implicit class OptionImprovements[A <: Ordered[A]](thisOption: Option[A]) {
        def isGreaterThan(option: Option[A]): Boolean = {
            (thisOption, option) match {
                case (Some(thisValue), Some(value)) =>
                    thisValue > value
                case (Some(_), None) =>
                    true
                case (None, Some(_)) =>
                    false
                case (None, None) =>
                    false
            }
        }
    }
    */

}
