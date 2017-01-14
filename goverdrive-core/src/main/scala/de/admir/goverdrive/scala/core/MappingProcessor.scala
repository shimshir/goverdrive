package de.admir.goverdrive.scala.core

import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import de.admir.goverdrive.scala.core.MappingUtils._
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.{FileMapping, FolderMapping}
import de.admir.goverdrive.scala.core.util.FileType._
import de.admir.goverdrive.scala.core.typeclasses.FileLike
import de.admir.goverdrive.scala.core.implicits.FileLike._
import java.io.{File => JFile}
import com.google.api.services.drive.model.{File => GFile}
import scala.language.postfixOps
import scala.util.Left


object MappingProcessor extends StrictLogging {
    def processMapping(localPath: String, remotePath: String): CoreFeedback Either Seq[CoreFeedback Either FileMapping] = {
        (localExists(localPath), remoteExists(remotePath)) match {
            case (true, true) => (getLocalFileType(localPath), getRemoteFileType(remotePath)) match {
                case (Right(localFileType), Right(remoteFileType)) if localFileType != remoteFileType =>
                    Left(CoreFeedback(s"Unequal file types for local ($localFileType): $localPath, remote ($remoteFileType): $remotePath"))
                case (Left(localCoreError), Left(remoteCoreError)) =>
                    Left(CoreFeedback(
                        s"Error while getting both local and remote fileType, localPath: $localPath, remotePath: $remotePath",
                        Seq(localCoreError, remoteCoreError)
                    ))
                case (Left(coreError), _) =>
                    Left(CoreFeedback(s"Error while getting fileType for localPath: $localPath", coreError))
                case (_, Left(coreError)) =>
                    Left(CoreFeedback(s"Error while getting fileType for remotePath: $remotePath", coreError))
                case (Right(FILE), Right(FILE)) =>
                    Right(Seq(createFileMapping(FileMapping(localPath = localPath, remotePath = remotePath))))
                case (Right(FOLDER), Right(FOLDER)) if !new JFile(localPath).listFiles().isEmpty =>
                    Left(CoreFeedback("You can not sync a non empty local folder with an existing remote folder"))
                case (Right(FOLDER), Right(FOLDER)) =>
                    Right(createFolderMapping[GFile](localPath, remotePath))
            }
            case (true, false) => getLocalFileType(localPath) match {
                case Left(coreError) =>
                    Left(CoreFeedback(s"Error while getting fileType for localPath: $localPath", coreError))
                case Right(FILE) =>
                    Right(Seq(createFileMapping(FileMapping(localPath = localPath, remotePath = remotePath))))
                case Right(FOLDER) =>
                    Right(createFolderMapping[JFile](localPath, remotePath))
            }
            case (false, true) => getRemoteFileType(remotePath) match {
                case Left(coreError) =>
                    Left(CoreFeedback(s"Error while getting fileType for remotePath: $remotePath", coreError))
                case Right(FILE) =>
                    Right(Seq(createFileMapping(FileMapping(localPath = localPath, remotePath = remotePath))))
                case Right(FOLDER) =>
                    Right(createFolderMapping[GFile](localPath, remotePath))
            }
            case (false, false) =>
                Left(CoreFeedback(s"Neither local nor remote file/folder does exist, localPath: $localPath, remotePath: $remotePath"))
        }
    }

    private def createFolderMapping[F](localPath: String, remotePath: String)
                                      (implicit ev: FileLike[F]): Seq[CoreFeedback Either FileMapping] = {
        val folderMapping = FolderMapping(localPath = localPath, remotePath = remotePath)
        GoverdriveDb.insertFolderMapping(folderMapping) match {
            case Left(t) =>
                Seq(Left(CoreFeedback("Error while inserting local folder", t)))
            case Right(persistedFolderMapping) =>
                ev.folder(persistedFolderMapping) match {
                    case Left(coreFeedback) =>
                        Seq(Left(coreFeedback))
                    case Right(folder) => ev.fileTree(folder, onlyFiles = true) map {
                        case Left(coreFeedback) =>
                            Left(coreFeedback)
                        case Right(file) =>
                            createFileMapping(ev.relativeFolderFileMapping(file, persistedFolderMapping))
                    }
                }
        }
    }

    private def createFileMapping(fileMapping: FileMapping): CoreFeedback Either FileMapping = {
        GoverdriveDb.insertFileMapping(fileMapping
        ) match {
            case Right(persistedFileMapping) =>
                logger.info(s"Successfully added file mapping: $persistedFileMapping")
                Right(persistedFileMapping)
            case Left(t) =>
                val errorMessage = "Could not insert file mapping"
                logger.error(errorMessage, t)
                Left(CoreFeedback(errorMessage, t))
        }
    }
}
