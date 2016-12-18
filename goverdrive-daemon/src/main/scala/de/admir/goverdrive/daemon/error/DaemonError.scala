package de.admir.goverdrive.daemon.error
import de.admir.goverdrive.java.core.error.{BaseError, CoreError}

case class DaemonError(message: String, nestedErrors: Option[Seq[CoreError]]) extends BaseError[DaemonError]
object DaemonError {
    def apply(message: String): DaemonError = new DaemonError(message, None)
    def apply(throwable: Throwable) = new DaemonError(throwable.toString, None)
    def apply(message: String, nestedError: CoreError) = new DaemonError(message, Some(Seq(nestedError)))
}
