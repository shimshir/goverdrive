package de.admir.goverdrive.scala.core

import java.io.ByteArrayOutputStream

import com.google.api.services.drive.model.File
import de.admir.goverdrive.java.core.{GoverdriveService, GoverdriveServiceImpl}
import de.admir.goverdrive.java.core.error.DriveError


object GoverdriveServiceWrapper {
    private val gs: GoverdriveService = new GoverdriveServiceImpl()

    import de.admir.goverdrive.scala.core.util.Conversions._

    def getFileStream(path: String): DriveError Either ByteArrayOutputStream = gs.getFileStream(path)

    def createFile(localPath: String, remotePath: String): DriveError Either File = gs.createFile(localPath, remotePath)

    def getRootFolder: DriveError Either File = gs.getRootFolder

    def getAllFilesAndFolders[T]: DriveError Either Seq[File] = gs.getAllFilesAndFolders

    def findFile(remotePath: String): DriveError Either File = gs.getFile(remotePath)

    def getFilePathList(remotePath: String): DriveError Either Seq[File] = gs.getFilePathList(remotePath)

    def createFolder(remotePath: String, createIntermediate: Boolean): DriveError Either File = gs.createFolder(remotePath, createIntermediate)
}
