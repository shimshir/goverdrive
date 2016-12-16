package de.admir.goverdrive.client.db

import slick.driver.SQLiteDriver.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import _root_.slick.driver.SQLiteDriver.backend.DatabaseDef
import de.admir.goverdrive.core.model.FileMapping

import slick.jdbc.meta.MTable
import slick.lifted.TableQuery
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

object SqLite {
    private val timeout = 30.seconds
    private val db: DatabaseDef = Database.forConfig("goverdrive.db")

    case class FileMappings(tag: Tag) extends Table[(String, String, String)](tag, "FILE_MAPPING") {
        def id = column[String]("ID", O.PrimaryKey)
        def localPath = column[String]("LOCAL_PATH")
        def remotePath = column[String]("REMOTE_PATH")
        def * = (id, localPath, remotePath)
    }

    private val fileMappings = TableQuery[FileMappings]

    private val setup = DBIO.seq(
        fileMappings.schema.create,

        fileMappings += ("QWERTY", "/local/path", "/remote/path")
    )

    def setupSync(): Unit = {
        Await.result(db.run(setup), timeout)
    }

    val tableNamesFuture: Future[Vector[String]] = db.run(MTable.getTables).map(tables => tables.map(_.name.name))

    def shouldSetupDbSync(): Boolean = Await.result(tableNamesFuture.map(tNames => !tNames.contains("FILE_MAPPING")), timeout)

    if (shouldSetupDbSync())
        setupSync()

    def insertFileMapping(fileMapping: FileMapping): Future[Int] = db.run(fileMappings += FileMapping.unapply(fileMapping).get)

    def insertFileMappingSync(fileMapping: FileMapping): Throwable Either Int = {
        val insertFuture = insertFileMapping(fileMapping)
        Either.catchNonFatal(Await.result(insertFuture, timeout))
    }
}
