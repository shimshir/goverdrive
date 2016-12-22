package de.admir.goverdrive.java.core;

import com.google.api.services.drive.model.File;

import de.admir.goverdrive.java.core.error.DriveError;
import de.admir.goverdrive.java.core.util.Xor;

import java.io.ByteArrayOutputStream;
import java.util.List;


public interface GoverdriveService {

    Xor<DriveError, ByteArrayOutputStream> getFileStream(String path);

    Xor<DriveError, File> createFile(String localPath, String remotePath);

    Xor<DriveError, File> getRootFolder();

    Xor<DriveError, List<File>> getAllFilesAndFolders();

    Xor<DriveError, File> getFile(String remotePath);

    Xor<DriveError, List<File>> getFilePathList(String remotePath);

    Xor<DriveError, File> createFolder(String remotePath, boolean createIntermediate);
}
