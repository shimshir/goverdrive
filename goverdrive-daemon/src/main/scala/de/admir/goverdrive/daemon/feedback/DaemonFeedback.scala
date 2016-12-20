package de.admir.goverdrive.daemon.feedback

import de.admir.goverdrive.java.core.error.CoreError
import de.admir.goverdrive.scala.core.feedback.Feedback
import de.admir.goverdrive.scala.core.util.CaseClassBeautifier


case class DaemonFeedback(message: String, nestedFeedbacks: Seq[Feedback] = Nil) extends Feedback {
    override def toString: String = CaseClassBeautifier.nice(this)
}

object DaemonFeedback {
    def apply(message: String, throwable: Throwable) = new DaemonFeedback(s"$message, $throwable")

    def apply(throwable: Throwable) = new DaemonFeedback(throwable.toString)

    def apply(message: String, nestedFeedback: Feedback) = new DaemonFeedback(message, Seq(nestedFeedback))

    def apply(message: String, coreError: CoreError) = new DaemonFeedback(s"$message, ${coreError.toString}")
}
