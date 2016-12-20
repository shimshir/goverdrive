package de.admir.goverdrive.client.feedback

import de.admir.goverdrive.java.core.error.CoreError
import de.admir.goverdrive.scala.core.feedback.Feedback
import de.admir.goverdrive.scala.core.util.CaseClassBeautifier


case class ClientFeedback(message: String, nestedFeedbacks: Seq[Feedback] = Nil) extends Feedback {
    override def toString: String = CaseClassBeautifier.nice(this)
}

object ClientFeedback {
    def apply(message: String, throwable: Throwable) = new ClientFeedback(s"$message, $throwable")

    def apply(throwable: Throwable) = new ClientFeedback(throwable.toString)

    def apply(message: String, nestedFeedback: Feedback) = new ClientFeedback(message, Seq(nestedFeedback))

    def apply(message: String, coreError: CoreError) = new ClientFeedback(s"$message, ${coreError.toString}")
}
