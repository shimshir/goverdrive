package de.admir.goverdrive.scala.core.db

import java.io.File
import java.sql.Timestamp

import de.admir.goverdrive.java.core.config.CoreConfig
import slick.driver.SQLiteDriver.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import de.admir.goverdrive.scala.core.model.FileMapping
import de.admir.goverdrive.scala.core.util.CoreUtils.catchNonFatal
import slick.jdbc.meta.MTable
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext.Implicits.global

object GoverdriveDb {
    private val timeout = 30.seconds
    private val dbFile = new File(CoreConfig.getDbFilePath)
    private val dbFolder = new File(CoreConfig.getDbFolder)
    private val db = Database.forConfig("goverdrive.db")

    case class FileMappings(tag: Tag) extends Table[FileMapping](tag, "FILE_MAPPING") {
        def pk = column[Option[Int]]("PK", O.PrimaryKey, O.AutoInc)

        def fileId = column[Option[String]]("FILE_ID")

        def localPath = column[String]("LOCAL_PATH")

        def remotePath = column[String]("REMOTE_PATH")

        def syncedAt = column[Option[Timestamp]]("SYNCED_AT")

        def idx = index("IDX_FILE_ID", fileId, unique = true)

        override def * = (pk, fileId, localPath, remotePath, syncedAt) <> (FileMapping.tupled, FileMapping.unapply)
    }

    private val fileMappings = TableQuery[FileMappings]

    private val setupSchemaAction = fileMappings.schema.create

    def tableNamesFuture: Future[Vector[String]] = db.run(MTable.getTables).map(_.map(_.name.name))

    def insertFileMapping(fileMapping: FileMapping): Future[FileMapping] = {
        val insertAction = (fileMappings returning fileMappings.map(_.pk)) += fileMapping
        db.run(insertAction).map(pk => fileMapping.copy(pk = pk))
    }

    def insertFileMappingSync(fileMapping: FileMapping): Throwable Either FileMapping = catchNonFatal {
        Await.result(insertFileMapping(fileMapping), timeout)
    }

    def getFileMappings: Future[Seq[FileMapping]] = db.run(fileMappings.result)

    def getFileMappingsSync: Throwable Either Seq[FileMapping] = catchNonFatal {
        Await.result(getFileMappings, timeout)
    }

    private def initDb(): Unit = {
        def shouldSetupFolderStructure(): Boolean = dbFolder.exists()
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
