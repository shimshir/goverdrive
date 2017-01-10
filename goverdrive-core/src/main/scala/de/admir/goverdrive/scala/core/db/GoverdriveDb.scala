package de.admir.goverdrive.scala.core.db

import java.io.File
import java.sql.Timestamp

import de.admir.goverdrive.java.core.config.CoreConfig
import de.admir.goverdrive.scala.core.feedback.CoreFeedback
import slick.driver.SQLiteDriver.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import de.admir.goverdrive.scala.core.model.{FileMapping, LocalFolder}
import de.admir.goverdrive.scala.core.util.CoreUtils.catchNonFatal
import slick.jdbc.meta.MTable
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext.Implicits.global


object GoverdriveDb {
    private val timeout = 30.seconds
    private val dbFile = new File(CoreConfig.getDbFilePath)
    private val dbFolder = new File(CoreConfig.getDbFolder)
    private val db = Database.forConfig("goverdrive.db")

    case class LocalFolders(tag: Tag) extends Table[LocalFolder](tag, "LOCAL_FOLDERS") {
        def pk = column[Option[Int]]("PK", O.PrimaryKey, O.AutoInc)

        def path = column[String]("PATH")

        def syncedAt = column[Option[Timestamp]]("SYNCED_AT")

        def pathIndex = index("IDX_PATH", path, unique = true)

        override def * = (pk, path, syncedAt) <> (LocalFolder.tupled, LocalFolder.unapply)
    }

    private val localFolders = TableQuery[LocalFolders]

    case class FileMappings(tag: Tag) extends Table[FileMapping](tag, "FILE_MAPPING") {
        def pk = column[Option[Int]]("PK", O.PrimaryKey, O.AutoInc)

        def fileId = column[Option[String]]("FILE_ID")

        def localPath = column[String]("LOCAL_PATH")

        def remotePath = column[String]("REMOTE_PATH")

        def syncedAt = column[Option[Timestamp]]("SYNCED_AT")

        def localFolderPk = column[Option[Int]]("LOCAL_FOLDER_PK")

        def localFolderFk = foreignKey("LOCAL_FOLDER_FK", localFolderPk, localFolders)(_.pk, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)

        def fileIdIndex = index("IDX_FILE_ID", fileId, unique = true)

        def localPathIndex = index("IDX_LOCAL_PATH", localPath, unique = true)

        override def * = (pk, fileId, localPath, remotePath, syncedAt, localFolderPk) <> (FileMapping.tupled, FileMapping.unapply)
    }

    private val fileMappings = TableQuery[FileMappings]

    private val setupSchemaAction = DBIO.seq(
        (localFolders.schema ++ fileMappings.schema).create
    )

    def tableNamesFuture: Future[Vector[String]] = db.run(MTable.getTables).map(_.map(_.name.name))


    // *** fileMappings *** \\

    def getFileMappingsFuture: Future[Seq[FileMapping]] = db.run(fileMappings.result)

    def getFileMappings: Throwable Either Seq[FileMapping] = catchNonFatal {
        Await.result(getFileMappingsFuture, timeout)
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

    def deleteFileMappingFuture(pk: Int): Future[CoreFeedback Either Int] = {
        val deleteAction = fileMappings.filter(_.pk === pk).delete
        db.run(deleteAction) map {
            case 0 => Left(CoreFeedback(s"No fileMapping was deleted for pk: $pk"))
            case affectedRows => Right(affectedRows)
        }
    }

    def deleteFileMapping(pk: Int): CoreFeedback Either Int = {
        catchNonFatal {
            Await.result(deleteFileMappingFuture(pk), timeout)
        } match {
            case Left(t) => Left(CoreFeedback(s"Error while trying to delete fileMapping, pk: $pk", t))
            case Right(result) => result
        }
    }

    // *** localFolders *** \\

    def getLocalFoldersFuture: Future[Seq[LocalFolder]] = db.run(localFolders.result)

    def getLocalFolderFuture(pk: Int): Future[Option[LocalFolder]] = {
        val queryAction = localFolders.filter(_.pk === pk).result.headOption
        db.run(queryAction)
    }

    def updateLocalFolderFuture(localFolder: LocalFolder): Future[Option[LocalFolder]] = {
        val updateAction = localFolders.filter(_.pk === localFolder.pk) update localFolder
        db.run(updateAction).map {
            case 0 => None
            case _ => Some(localFolder)
        }
    }

    def insertLocalFolderFuture(localFolder: LocalFolder): Future[LocalFolder] = {
        val insertAction = (localFolders returning localFolders.map(_.pk)) += localFolder
        db.run(insertAction).map(pk => localFolder.copy(pk = pk))
    }

    def insertLocalFolder(localFolder: LocalFolder): Throwable Either LocalFolder = catchNonFatal {
        Await.result(insertLocalFolderFuture(localFolder), timeout)
    }

    def deleteLocalFolderFuture(pk: Int): Future[CoreFeedback Either Int] = {
        val deleteAction = localFolders.filter(_.pk === pk).delete
        db.run(deleteAction) map {
            case 0 => Left(CoreFeedback(s"No localFolder was deleted for pk: $pk"))
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
