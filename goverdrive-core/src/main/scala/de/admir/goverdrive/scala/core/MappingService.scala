package de.admir.goverdrive.scala.core

import java.io.File

import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}
import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import de.admir.goverdrive.scala.core.model.FileMapping
import de.admir.goverdrive.scala.core.util.FileType._


object MappingService {

    def getLocalFileType(path: String): CoreFeedback Either FileType = {
        val file = new File(path)
        if (file.isFile)
            Right(FILE)
        else if (file.isDirectory)
            Right(FOLDER)
        else
            Left(CoreFeedback(s"Unsupported local file type, path: $path"))
    }

    def getRemoteFileType(path: String): CoreFeedback Either FileType = {
        GoverdriveService.getFile(path) match {
            case Left(error) => Left(CoreFeedback(s"Remote file not found, path: $path", error))
            case Right(file) => file.getMimeType match {
                case "application/vnd.google-apps.file" => Right(FILE)
                case "application/vnd.google-apps.folder" => Right(FOLDER)
                case mimeType => Left(CoreFeedback(s"Unsupported mimeType: $mimeType for remote file: $path"))
            }
        }
    }

    def localExists(path: String): Boolean = {
        new File(path).exists()
    }

    def remoteExists(path: String): Boolean = {
        GoverdriveService.getFile(path).isRight
    }

    def localExists(fileMapping: FileMapping): Boolean = {
        localExists(fileMapping.localPath)
    }

    def remoteExists(fileMapping: FileMapping): Boolean = {
        remoteExists(fileMapping.remotePath)
    }

    def localTimestamp(fileMapping: FileMapping): Long = {
        new File(fileMapping.localPath).lastModified()
    }

    def remoteTimestamp(fileMapping: FileMapping): Long = {
        GoverdriveService.getFile(fileMapping.remotePath).fold(_ => 0, _.getModifiedTime.getValue)
    }
}
