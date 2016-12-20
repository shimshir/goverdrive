package de.admir.goverdrive.scala.core.feedback

import de.admir.goverdrive.java.core.error.CoreError
import de.admir.goverdrive.scala.core.util.CaseClassBeautifier

case class CoreFeedback(message: String, nestedFeedbacks: Seq[Feedback] = Nil) extends Feedback {
    override def toString: String = CaseClassBeautifier.nice(this)
}

object CoreFeedback {
    def apply(message: String, throwable: Throwable) = new CoreFeedback(s"$message, $throwable")

    def apply(throwable: Throwable) = new CoreFeedback(throwable.toString)

    def apply(message: String, nestedFeedback: Feedback) = new CoreFeedback(message, Seq(nestedFeedback))

    def apply(message: String, coreError: CoreError) = new CoreFeedback(s"$message, ${coreError.toString}")
}
