package de.admir.goverdrive.scala.core

import java.io.ByteArrayOutputStream

import com.google.api.services.drive.model.{File => GFile}
import de.admir.goverdrive.java.core.{GoverdriveService, GoverdriveServiceImpl}
import de.admir.goverdrive.java.core.error.DriveError


object GoverdriveServiceWrapper {
    private val gs: GoverdriveService = new GoverdriveServiceImpl()

    import de.admir.goverdrive.scala.core.util.Conversions._

    def getFileStream(path: String): DriveError Either ByteArrayOutputStream = gs.getFileStream(path)

    def createFile(localPath: String, remotePath: String, overwrite: Boolean = true): DriveError Either GFile = gs.createFile(localPath, remotePath, overwrite)

    def getRootFolder: DriveError Either GFile = gs.getRootFolder

    def getAllFilesAndFolders[T]: DriveError Either Seq[GFile] = gs.getAllFilesAndFolders

    def getFile(remotePath: String): DriveError Either GFile = gs.getFile(remotePath)

    def getFilePathList(remotePath: String): DriveError Either Seq[GFile] = gs.getFilePathList(remotePath)

    def deleteFile(remotePath: String): DriveError Either Unit = xor2Either(gs.deleteFile(remotePath)) match {
        case Right(_) => Right(())
        case Left(driveError) => Left(driveError)
    }
}
