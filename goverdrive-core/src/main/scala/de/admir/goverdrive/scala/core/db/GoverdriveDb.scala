package de.admir.goverdrive.scala.core.db

import java.io.File
import java.sql.Timestamp

import de.admir.goverdrive.java.core.config.CoreConfig
import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import slick.driver.SQLiteDriver.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import de.admir.goverdrive.scala.core.model.{FileMapping, FolderMapping}
import de.admir.goverdrive.scala.core.util.CoreUtils.catchNonFatal
import slick.jdbc.meta.MTable
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext.Implicits.global


object GoverdriveDb {
    private val timeout = 30.seconds
    private val dbFile = new File(CoreConfig.getDbFilePath)
    private val dbFolder = new File(CoreConfig.getDbFolder)
    private val db = Database.forConfig("goverdrive.db")

    case class FolderMappings(tag: Tag) extends Table[FolderMapping](tag, "LOCAL_FOLDERS") {
        def pk = column[Option[Int]]("PK", O.PrimaryKey, O.AutoInc)
        def localPath = column[String]("LOCAL_PATH")
        def remotePath = column[String]("REMOTE_PATH")
        def syncedAt = column[Option[Timestamp]]("SYNCED_AT")

        def localPathIndex = index("IDX_FOLDER_LOCAL_PATH", localPath, unique = true)
        def remotePathIndex = index("IDX_FOLDER_REMOTE_PATH", remotePath, unique = true)

        override def * = (pk, localPath, remotePath, syncedAt) <> (FolderMapping.tupled, FolderMapping.unapply)
    }

    private val folderMappings = TableQuery[FolderMappings]

    case class FileMappings(tag: Tag) extends Table[FileMapping](tag, "FILE_MAPPING") {
        def pk = column[Option[Int]]("PK", O.PrimaryKey, O.AutoInc)
        def fileId = column[Option[String]]("FILE_ID")
        def localPath = column[String]("LOCAL_PATH")
        def remotePath = column[String]("REMOTE_PATH")
        def syncedAt = column[Option[Timestamp]]("SYNCED_AT")
        def folderMappingPk = column[Option[Int]]("FOLDER_MAPPING_PK")

        def folderMappingFk = foreignKey("FOLDER_MAPPING_FK", folderMappingPk, folderMappings)(_.pk, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
        def fileIdIndex = index("IDX_FILE_ID", fileId, unique = true)
        def localPathIndex = index("IDX_FILE_LOCAL_PATH", localPath, unique = true)

        override def * = (pk, fileId, localPath, remotePath, syncedAt, folderMappingPk) <> (FileMapping.tupled, FileMapping.unapply)
    }

    private val fileMappings = TableQuery[FileMappings]

    private val setupSchemaAction = DBIO.seq(
        (folderMappings.schema ++ fileMappings.schema).create
    )

    def tableNamesFuture: Future[Vector[String]] = db.run(MTable.getTables).map(_.map(_.name.name))


    // *** fileMappings *** \\

    def getFileMappingsFuture: Future[Seq[FileMapping]] = db.run(fileMappings.result)

    def getFileMappings: Throwable Either Seq[FileMapping] = catchNonFatal {
        Await.result(getFileMappingsFuture, timeout)
    }

    def getFileMappingsByFolderMappingPkFuture(folderMappingPk: Option[Int]): Future[Seq[FileMapping]] = {
        val queryAction = fileMappings.filter(_.folderMappingPk === folderMappingPk).result
        db.run(queryAction)
    }

    def getFileMappingsByFolderMappingPk(pk: Option[Int]): Throwable Either Seq[FileMapping] = catchNonFatal {
        Await.result(getFileMappingsByFolderMappingPkFuture(pk), timeout)
    }

    def updateFileMappingFuture(fileMapping: FileMapping): Future[Option[FileMapping]] = {
        val updateAction = fileMappings.filter(_.pk === fileMapping.pk) update fileMapping
        db.run(updateAction).map {
            case 0 => None
            case _ => Some(fileMapping)
        }
    }

    def insertFileMappingFuture(fileMapping: FileMapping): Future[FileMapping] = {
        val insertAction = (fileMappings returning fileMappings.map(_.pk)) += fileMapping
        db.run(insertAction).map(pk => fileMapping.copy(pk = pk))
    }

    def insertFileMapping(fileMapping: FileMapping): Throwable Either FileMapping = catchNonFatal {
        Await.result(insertFileMappingFuture(fileMapping), timeout)
    }

    def deleteFileMappingFuture(pk: Option[Int]): Future[Int] = {
        val deleteAction = fileMappings.filter(_.pk === pk).delete
        db.run(deleteAction)
    }

    def deleteFileMapping(pk: Option[Int]): Throwable Either Int = catchNonFatal {
        Await.result(deleteFileMappingFuture(pk), timeout)
    }

    // *** folderMappings *** \\

    def getFolderMappingsFuture: Future[Seq[FolderMapping]] = db.run(folderMappings.result)

    def getFolderMappingFuture(pk: Int): Future[Option[FolderMapping]] = {
        getFolderMappingFuture(Some(pk)).map(_.headOption)
    }

    def getFolderMappingFuture(pk: Option[Int]): Future[Seq[FolderMapping]] = {
        val queryAction = folderMappings.filter(_.pk === pk).result
        db.run(queryAction)
    }

    def updateFolderMappingFuture(folderMapping: FolderMapping): Future[Option[FolderMapping]] = {
        val updateAction = folderMappings.filter(_.pk === folderMapping.pk) update folderMapping
        db.run(updateAction).map {
            case 0 => None
            case _ => Some(folderMapping)
        }
    }

    def insertFolderMappingFuture(folderMapping: FolderMapping): Future[FolderMapping] = {
        val insertAction = (folderMappings returning folderMappings.map(_.pk)) += folderMapping
        db.run(insertAction).map(pk => folderMapping.copy(pk = pk))
    }

    def insertFolderMapping(folderMapping: FolderMapping): Throwable Either FolderMapping = catchNonFatal {
        Await.result(insertFolderMappingFuture(folderMapping), timeout)
    }

    def deleteFolderMappingFuture(pk: Int): Future[CoreFeedback Either Int] = {
        val deleteAction = folderMappings.filter(_.pk === pk).delete
        db.run(deleteAction) map {
            case 0 => Left(CoreFeedback(s"No folderMapping was deleted for pk: $pk"))
            case affectedRows => Right(affectedRows)
        }
    }

    def initDb(): Unit = {
        def shouldSetupFolderStructure(): Boolean = !dbFolder.exists()

        def setupFolderStructure(): Unit = dbFolder.mkdirs()

        def shouldSetupDbSync(): Boolean = Await.result(tableNamesFuture.map(!_.contains("FILE_MAPPING")), timeout)

        def setupDbSync(): Unit = Await.result(db.run(setupSchemaAction), timeout)

        if (shouldSetupFolderStructure())
            setupFolderStructure()
        if (shouldSetupDbSync())
            setupDbSync()
    }

    def tearDownDb(): Unit = {
        dbFile.delete()
    }

    initDb()
}
