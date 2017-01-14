package de.admir.goverdrive.scala.core

import java.io.{File => JFile}

import com.google.api.services.drive.model.{File => GFile}
import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.scala.core.{GoverdriveServiceWrapper => GoverdriveService}
import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import de.admir.goverdrive.scala.core.model.{FileMapping, FolderMapping}
import de.admir.goverdrive.scala.core.typeclasses.FileLike

import scala.util.{Failure, Success, Try}

package object implicits extends StrictLogging {

    object FileLike {

        implicit object FileLikeJFile extends FileLike[JFile] {
            override def fileTree(file: JFile, onlyFiles: Boolean = false): Seq[CoreFeedback Either JFile] = {
                def innerFileTree(file: JFile): Seq[CoreFeedback Either JFile] = {
                    val tl = Try(if (file.isDirectory) file.listFiles().toList.flatMap(innerFileTree) else Nil) match {
                        case Failure(t) => Seq(Left(CoreFeedback(t)))
                        case Success(childFiles) => childFiles
                    }
                    if (file.isDirectory && onlyFiles)
                        tl
                    else
                        Right(file) +: tl
                }

                innerFileTree(file)
            }

            override def path(file: JFile): String = file.getAbsolutePath

            override def folder(folderMapping: FolderMapping): CoreFeedback Either JFile = {
                val jFile = new JFile(folderMapping.localPath)
                if (jFile.canRead)
                    Right(jFile)
                else
                    Left(CoreFeedback(s"Could not read local folder: ${folderMapping.localPath}"))
            }


            override def isDirectory(file: JFile): Boolean = file.isDirectory

            override def origin: String = "local"

            override def relativeFolderFileMapping(file: JFile, folderMapping: FolderMapping): FileMapping = {
                val originPath = path(file)
                val relativeLocalPath = originPath.replaceFirst(folderMapping.localPath, "")
                val absoluteRemotePath = folderMapping.remotePath + relativeLocalPath
                FileMapping(localPath = originPath, remotePath = absoluteRemotePath, folderMappingPk = folderMapping.pk)
            }

            override protected def listFiles(file: JFile): Seq[JFile] = file.listFiles()
        }

        implicit object FileLikeGFile extends FileLike[GFile] {
            override def fileTree(file: GFile, onlyFiles: Boolean = false): Seq[CoreFeedback Either GFile] = {
                def innerFileTree(file: GFile): Seq[CoreFeedback Either GFile] = {
                    val tl = Try(if (isDirectory(file)) listFiles(file).toList.flatMap(innerFileTree) else Nil) match {
                        case Failure(t) => Seq(Left(CoreFeedback(t)))
                        case Success(childFiles) => childFiles
                    }
                    if (isDirectory(file) && onlyFiles)
                        tl
                    else
                        Right(file) +: tl
                }

                innerFileTree(file)
            }

            override def path(file: GFile): String =
                GoverdriveService.getAllFilesAndFolders match {
                    case Left(driveError) =>
                        logger.error(s"I really have no idea how this happened, driveError: $driveError")
                        "i don't care, just fail it"
                    case Right(allGoogleFiles) =>
                        def getPath(files: Seq[GFile], currentFile: GFile): Seq[GFile] = {
                            if (currentFile.getParents != null && !currentFile.getParents.isEmpty)
                                getPath(files, files.find(_.getId == currentFile.getParents.get(0)).get) :+ currentFile
                            else
                                Seq(currentFile)
                        }

                        getPath(allGoogleFiles, file)
                            .tail
                            .foldRight("")((file, acc) => s"/${file.getName}$acc")
                }

            override def folder(folderMapping: FolderMapping): CoreFeedback Either GFile =
                GoverdriveService.getFile(folderMapping.remotePath) match {
                    case Left(driveError) => Left(CoreFeedback(s"Error while retrieving remote folder: ${folderMapping.remotePath}", driveError))
                    case Right(file) => Right(file)
                }

            override def isDirectory(file: GFile): Boolean = file.getMimeType == "application/vnd.google-apps.folder"

            override def origin: String = "remote"

            override def relativeFolderFileMapping(file: GFile, folderMapping: FolderMapping): FileMapping = {
                val originPath = path(file)
                val relativeRemotePath = originPath.replaceFirst(folderMapping.remotePath, "")
                val absoluteLocalPath = folderMapping.localPath + relativeRemotePath
                FileMapping(localPath = absoluteLocalPath, remotePath = originPath, folderMappingPk = folderMapping.pk)
            }

            override protected def listFiles(file: GFile): Seq[GFile] =
                GoverdriveService.getAllFilesAndFolders match {
                    case Left(driveError) =>
                        logger.error(s"I really have no idea how this happened, driveError: $driveError")
                        Seq()
                    case Right(allGoogleFiles) =>
                        allGoogleFiles.filter(gFile => {
                            if (gFile.getParents != null && !gFile.getParents.isEmpty)
                                gFile.getParents.contains(file.getId)
                            else
                                false
                        })
                }
        }

    }

}
