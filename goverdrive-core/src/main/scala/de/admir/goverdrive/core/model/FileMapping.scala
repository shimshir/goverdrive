package de.admir.goverdrive.core.model

case class FileMapping(pk: Option[Int] = None, fileId: Option[String] = None, localPath: String, remotePath: String)
