package de.admir.goverdrive.core;


import com.google.api.services.drive.model.File;

import de.admir.goverdrive.core.error.ApiError;
import de.admir.goverdrive.core.util.Xor;

import java.io.OutputStream;
import java.util.List;

public interface GoverdriveService {
    OutputStream getFileStream(String path);

    Xor<ApiError, File> createFile(String localPath, String remotePath);

    Xor<ApiError, File> getRootFolder();

    Xor<ApiError, List<File>> getAllFilesAndFolders();

    Xor<ApiError, List<File>> getFilePath(String remotePath);

    String createFolder(String path);
}
