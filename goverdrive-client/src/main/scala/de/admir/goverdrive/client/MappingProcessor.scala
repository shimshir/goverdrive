package de.admir.goverdrive.client

import java.io.File
import java.util.regex.Pattern
import com.typesafe.scalalogging.StrictLogging
import de.admir.goverdrive.client.feedback.ClientFeedback
import de.admir.goverdrive.java.core.util.SystemUtils
import de.admir.goverdrive.scala.core.MappingService._
import de.admir.goverdrive.scala.core.db.GoverdriveDb
import de.admir.goverdrive.scala.core.model.{FileMapping, FolderMapping}
import de.admir.goverdrive.scala.core.util.CoreUtils
import de.admir.goverdrive.scala.core.util.FileType._
import de.admir.goverdrive.scala.core.util.implicits._
import com.google.api.services.drive.model.{File => GFile}
import scala.language.postfixOps
import scala.util.Left


object MappingProcessor extends StrictLogging {
    def processMapping(localPath: String, remotePath: String): ClientFeedback Either Seq[ClientFeedback Either FileMapping] = {
        (localExists(localPath), remoteExists(remotePath)) match {
            case (true, true) => (getLocalFileType(localPath), getRemoteFileType(remotePath)) match {
                case (Right(localFileType), Right(remoteFileType)) if localFileType != remoteFileType =>
                    Left(ClientFeedback(s"Unequal file types for local ($localFileType): $localPath, remote ($remoteFileType): $remotePath"))
                case (Left(localCoreError), Left(remoteCoreError)) =>
                    Left(ClientFeedback(
                        s"Error while getting both local and remote fileType, localPath: $localPath, remotePath: $remotePath",
                        Seq(localCoreError, remoteCoreError)
                    ))
                case (Left(coreError), _) =>
                    Left(ClientFeedback(s"Error while getting fileType for localPath: $localPath", coreError))
                case (_, Left(coreError)) =>
                    Left(ClientFeedback(s"Error while getting fileType for remotePath: $remotePath", coreError))
                case (Right(FILE), Right(FILE)) =>
                    Right(Seq(insertFileMapping(localPath, remotePath)))
                case (Right(FOLDER), Right(FOLDER)) if !new File(localPath).listFiles().isEmpty =>
                    Left(ClientFeedback("You can not sync a non empty local folder with an existing remote folder"))
                case (Right(FOLDER), Right(FOLDER)) =>
                    Right(insertFolderMapping(localPath, remotePath, FolderSourceEnum.REMOTE))
            }
            case (true, false) => getLocalFileType(localPath) match {
                case Left(coreError) =>
                    Left(ClientFeedback(s"Error while getting fileType for localPath: $localPath", coreError))
                case Right(FILE) =>
                    Right(Seq(insertFileMapping(localPath, remotePath)))
                case Right(FOLDER) =>
                    Right(insertFolderMapping(localPath, remotePath, FolderSourceEnum.LOCAL))
            }
            case (false, true) => getRemoteFileType(remotePath) match {
                case Left(coreError) =>
                    Left(ClientFeedback(s"Error while getting fileType for remotePath: $remotePath", coreError))
                case Right(FILE) =>
                    Right(Seq(insertFileMapping(localPath, remotePath)))
                case Right(FOLDER) =>
                    Right(insertFolderMapping(localPath, remotePath, FolderSourceEnum.REMOTE))
            }
            case (false, false) =>
                Left(ClientFeedback(s"Neither local nor remote file/folder does exist, localPath: $localPath, remotePath: $remotePath"))
        }
    }

    private def insertFileMapping(localPath: String, remotePath: String): ClientFeedback Either FileMapping = {
        insertMapping(localPath, remotePath)
    }

    private object FolderSourceEnum extends Enumeration {
        type FolderSourceEnum = Value
        val LOCAL = Value("local")
        val REMOTE = Value("remote")
    }

    import FolderSourceEnum._

    private def insertFolderMapping(localPath: String, remotePath: String, source: FolderSourceEnum): Seq[ClientFeedback Either FileMapping] = {
        GoverdriveDb.insertFolderMapping(FolderMapping(localPath = localPath, remotePath = remotePath)) match {
            case Left(t) =>
                Seq(Left(ClientFeedback("Error while inserting local folder", t)))
            case Right(folderMapping) => source match {
                case LOCAL =>
                    insertFolderMappingForLocal(localPath, remotePath, folderMapping.pk)
                case REMOTE =>
                    insertFolderMappingForRemote(localPath, remotePath, folderMapping.pk)
            }
        }
    }


    private def insertFolderMappingForLocal(localPath: String, remotePath: String, folderMappingPk: Option[Int]): Seq[ClientFeedback Either FileMapping] = {
        val adjustedLocalPath = new File(localPath).getAbsolutePath

        CoreUtils.getLocalFileTree(adjustedLocalPath) match {
            case Left(coreFeedback) =>
                Seq(Left(ClientFeedback("Error while walking local file tree", coreFeedback)))
            case Right(files) =>
                files filter (!_.isDirectory) map { file =>
                    val relativeLocalPath = file.getAbsolutePath.replaceFirst(Pattern.quote(adjustedLocalPath), SystemUtils.EMPTY_STRING)
                    val absoluteRemotePath = remotePath + relativeLocalPath
                    insertMapping(file.getAbsolutePath, absoluteRemotePath.replaceAll("\\\\", "/") /*windows, what can you do*/ , folderMappingPk)
                }
        }
    }

    private def insertFolderMappingForRemote(localPath: String, remotePath: String, folderMappingPk: Option[Int]): Seq[ClientFeedback Either FileMapping] = {
        CoreUtils.getRemoteFileTree(remotePath) match {
            case Left(coreFeedback) =>
                Seq(Left(ClientFeedback("Error while walking remote file tree", coreFeedback)))
            case Right(files) =>
                files filter (!_.isDirectory) map { file: GFile =>
                    val relativeRemotePath = file.getAbsolutePath.replaceFirst(remotePath, SystemUtils.EMPTY_STRING)
                    val absoluteLocalPath = localPath + relativeRemotePath
                    insertMapping(absoluteLocalPath, file.getAbsolutePath, folderMappingPk)
                }
        }
    }

    private def insertMapping(localPath: String, remotePath: String, folderMappingPk: Option[Int] = None): ClientFeedback Either FileMapping = {
        GoverdriveDb.insertFileMapping(
            FileMapping(
                localPath = localPath,
                remotePath = remotePath,
                folderMappingPk = folderMappingPk
            )
        ) match {
            case Right(fileMapping) =>
                logger.info(s"Successfully added file mapping: $fileMapping")
                Right(fileMapping)
            case Left(t) =>
                val errorMessage = "Could not insert file mapping"
                logger.error(errorMessage, t)
                Left(ClientFeedback(errorMessage, t))
        }
    }
}
