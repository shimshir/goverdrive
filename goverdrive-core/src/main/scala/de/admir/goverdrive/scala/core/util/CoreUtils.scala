package de.admir.goverdrive.scala.core.util

import java.io.File
import scala.util.{Failure, Success, Try}


object CoreUtils {
    def catchNonFatal[T](callable: => T): Throwable Either T = {
        Try(callable) match {
            case Success(value) => Right(value)
            case Failure(t) => Left(t)
        }
    }

    def getFileTree(f: File): Stream[File] =
        f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree) else Stream.empty)
}
