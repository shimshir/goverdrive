package de.admir.goverdrive.scala.core.feedback

trait Feedback {
    def message: String
    def nestedFeedbacks: Seq[Feedback]
}
