package de.admir.goverdrive.core.util


import scala.util.{Failure, Success, Try}

object CoreUtils {
    def catchNonFatal[T](callable: => T): Throwable Either T = {
        Try(callable) match {
            case Success(value) => Right(value)
            case Failure(t) => Left(t)
        }
    }
}
