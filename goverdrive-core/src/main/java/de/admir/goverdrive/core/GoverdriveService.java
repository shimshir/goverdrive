package de.admir.goverdrive.core;


import com.google.api.services.drive.model.File;

import de.admir.goverdrive.core.error.DriveError;
import de.admir.goverdrive.core.util.Xor;

import java.io.OutputStream;
import java.util.List;

public interface GoverdriveService {
    OutputStream getFileStream(String path);

    Xor<DriveError, File> createFile(String localPath, String remotePath);

    Xor<DriveError, File> getRootFolder();

    Xor<DriveError, List<File>> getAllFilesAndFolders();

    Xor<DriveError, File> findFile(String remotePath);

    Xor<DriveError, List<File>> getFilePath(String remotePath);

    Xor<DriveError, File> createFolder(String remotePath);
}
