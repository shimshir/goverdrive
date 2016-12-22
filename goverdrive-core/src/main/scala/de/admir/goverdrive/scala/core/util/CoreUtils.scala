package de.admir.goverdrive.scala.core.util


import java.io.{File => JFile}

import de.admir.goverdrive.scala.core.util.implicits._
import com.google.api.services.drive.model.{File => GFile}
import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}

import scala.util.{Failure, Success, Try}


object CoreUtils {
    def catchNonFatal[T](callable: => T): Throwable Either T = {
        Try(callable) match {
            case Success(value) => Right(value)
            case Failure(t) => Left(t)
        }
    }

    def getLocalFileTree(path: String): CoreFeedback Either Seq[JFile] = {
        def innerFileTree(f: JFile): Seq[JFile] =
            f :: (if (f.isDirectory) f.listFiles().toList.flatMap(innerFileTree) else Nil)

        Try(innerFileTree(new JFile(path))) match {
            case Failure(t) => Left(CoreFeedback(t))
            case Success(files) => Right(files)
        }
    }

    def getRemoteFileTree(path: String): CoreFeedback Either Seq[GFile] = {
        GoverdriveService.getFile(path) match {
            case Left(driveError) => Left(CoreFeedback("Error while getting remote file path", driveError))
            case Right(f) =>
                def innerFileTree(f: GFile): Seq[GFile] =
                    f :: (if (f.isDirectory) f.listFiles().toList.flatMap(innerFileTree) else Nil)

                Try(innerFileTree(f)) match {
                    case Failure(t) => Left(CoreFeedback(t))
                    case Success(files) => Right(files)
                }
        }
    }
}
