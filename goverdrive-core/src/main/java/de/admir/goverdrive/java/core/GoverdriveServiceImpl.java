package de.admir.goverdrive.java.core;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import de.admir.goverdrive.java.core.cache.CacheService;
import de.admir.goverdrive.java.core.config.CoreConfig;
import de.admir.goverdrive.java.core.error.DriveError;
import de.admir.goverdrive.java.core.error.AuthorizationError;
import de.admir.goverdrive.java.core.error.IOError;
import de.admir.goverdrive.java.core.util.SystemUtils;
import de.admir.goverdrive.java.core.util.Xor;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import static de.admir.goverdrive.java.core.error.DriveError.*;


public class GoverdriveServiceImpl implements GoverdriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoverdriveServiceImpl.class);

    private static final java.io.File DATA_STORE_DIR = new java.io.File(CoreConfig.CONFIG.getString("goverdrive.credentials.url"));

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);

    private static final HttpTransport HTTP_TRANSPORT = SystemUtils
        .handleFatal(GoogleNetHttpTransport::newTrustedTransport, e -> logger.error(MarkerFactory.getMarker("FATAL"), "Could not instantiate HTTP_TRANSPORT", e));

    private static final FileDataStoreFactory DATA_STORE_FACTORY = SystemUtils
        .handleFatal(() -> new FileDataStoreFactory(DATA_STORE_DIR), e -> logger.error(MarkerFactory.getMarker("FATAL"), "Could not instantiate DATA_STORE_FACTORY", e));

    private static final GoogleClientSecrets CLIENT_SECRETS = SystemUtils
        .handleFatal(() -> GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(GoverdriveServiceImpl.class.getResourceAsStream("/client_creds.json"))),
            e -> logger.error(MarkerFactory.getMarker("FATAL"), "Could not instantiate CLIENT_SECRETS", e));

    private static final String FILE_FIELDS = "id, kind, mimeType, name, parents, modifiedTime";

    @Override
    public Xor<DriveError, ByteArrayOutputStream> getFileStream(String path) {
        return getFile(path)
            .mapRight(File::getId)
            .flatMapRight(fileId ->
                createAuthorizedDriveService()
                    .mapLeft(authError -> new DriveError("Error while creating authorized drive service", DriveErrorType.NESTED).addNestedError(authError))
                    .flatMapRight(driveService ->
                        Xor.catchNonFatal(() -> {
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                            return outputStream;
                        }).mapLeft(e -> new DriveError("Error while downloading file content", DriveErrorType.NESTED).addNestedError(new IOError(e))))
            );
    }

    @Override
    public Xor<DriveError, File> createFile(String localPath, String remotePath) {
        logger.debug(String.format("Attempting to create file, localPath: %s, remotePath: %s", localPath, remotePath));

        if (getFile(remotePath).isRight())
            return Xor.left(new DriveError("File already exists, remotePath: " + remotePath, DriveErrorType.DUPLICATE_FILE));

        final java.io.File localFile = new java.io.File(localPath);
        Xor<IOError, FileContent> xorFileContent = loadFileContent(localFile);
        return xorFileContent
            .mapLeft(ioError -> new DriveError("Error while retrieving file content", DriveErrorType.NESTED).addNestedError(ioError)).flatMapRight(fileContent -> {

                List<String> filePath = pathToList(remotePath);

                Xor<DriveError, File> xorRemoteFolder = filePath.size() == 0 ?
                    Xor.left(new DriveError("You must provide at least a file name", DriveErrorType.ILLEGAL_ARGUMENTS)) :
                    createFolderWithIntermediate(SystemUtils.joinStrings(filePath.subList(0, filePath.size() - 1), "/"));

                return xorRemoteFolder.flatMapRight(
                    folder -> createAuthorizedDriveService()
                        .mapLeft(authError -> new DriveError("Error while creating drive service", DriveErrorType.NESTED).addNestedError(authError))
                        .flatMapRight(driveService -> Xor.catchNonFatal(() -> {
                            File fileMetadata = new File();
                            fileMetadata.setName(Paths.get(remotePath).getFileName().toString());
                            fileMetadata.setParents(Collections.singletonList(folder.getId()));
                            return driveService.files().create(fileMetadata, fileContent).setFields(FILE_FIELDS).execute();
                        }).mapLeft(e -> new DriveError("Error while creating file", DriveErrorType.NESTED).addNestedError(new IOError(e))).mapRight(file -> {
                            updateFilesAndFoldersCache(file);
                            return file;
                        })));
            });
    }

    @Override
    public Xor<DriveError, File> getRootFolder() {
        File cachedResult = CacheService.getRootFolder();
        if (cachedResult != null) {
            return Xor.right(cachedResult);
        } else {
            return createAuthorizedDriveService().mapLeft(authError -> new DriveError("Error while creating authorized drive service", DriveErrorType.NESTED).addNestedError(authError))
                .flatMapRight(driveService -> Xor.catchNonFatal(() -> driveService.files().get("root").execute()).mapLeft(DriveError::new)).mapRight(folder -> {
                    CacheService.updateRootFolder(folder);
                    return folder;
                });
        }
    }

    @Override
    public Xor<DriveError, List<File>> getAllFilesAndFolders() {
        List<File> cachedResult = CacheService.getAllFilesAndFolders();
        if (cachedResult != null) {
            return Xor.right(cachedResult);
        } else {
            return createAuthorizedDriveService()
                .mapLeft(authError -> new DriveError("Error while creating authorized drive service", DriveErrorType.NESTED).addNestedError(authError)).flatMapRight(
                    driveService -> Xor.catchNonFatal(() -> driveService.files().list().setFields(String.format("files (%s)", FILE_FIELDS)).execute().getFiles()).mapLeft(DriveError::new))
                .flatMapRight(files -> getRootFolder().mapRight(rootFolder -> addFirst(rootFolder, files))).mapRight(files -> {
                    CacheService.updateAllFilesAndFolders(files);
                    return files;
                });
        }
    }

    @Override
    public Xor<DriveError, File> getFile(String remotePath) {
        return getFilePathList(remotePath).mapRight(files -> files.get(files.size() - 1));
    }

    @Override
    public Xor<DriveError, List<File>> getFilePathList(String remotePath) {
        List<String> fileNames = pathToList(remotePath);

        final Xor<DriveError, List<File>> xorFiles = getAllFilesAndFolders();

        final Xor<DriveError, List<List<File>>> xorCandidatesList = xorFiles.flatMapRight(files -> {
            List<List<File>> candidatesList = new ArrayList<>();
            for (String fileName : addFirst("My Drive", fileNames)) {
                List<File> candidates = files.stream().filter(file -> fileName.equals(file.getName())).collect(Collectors.toList());
                if (candidates.size() == 0)
                    return Xor.left(new DriveError("No folder found named: " + fileName, DriveErrorType.FOLDER_NOT_FOUND));
                candidatesList.add(candidates);
            }

            return Xor.right(candidatesList);
        });

        Xor<DriveError, List<File>> xorCandidates = xorCandidatesList.flatMapRight(candidatesList -> {
            LinkedList<File> filteredCandidates = new LinkedList<>();
            for (int candidatesIndex = candidatesList.size() - 1; candidatesIndex > 0; candidatesIndex--) {
                List<File> files = candidatesList.get(candidatesIndex);
                List<File> possibleParents = candidatesList.get(candidatesIndex - 1);
                Xor<DriveError, File> xorConjunctFile = findConjunctFile(files, possibleParents);
                if (xorConjunctFile.isLeft()) {
                    return Xor.left(xorConjunctFile.getLeft());
                } else {
                    filteredCandidates.addFirst(xorConjunctFile.getRight());
                }
            }
            filteredCandidates.addFirst(candidatesList.get(0).get(0));
            return Xor.right(filteredCandidates);
        });

        return xorCandidates;
    }

    @Override
    public Xor<DriveError, File> createFolder(String remotePath, boolean createIntermediate) {
        return createIntermediate ? createFolderWithIntermediate(remotePath) : createFolderNoIntermediate(remotePath);
    }

    private static void updateFilesAndFoldersCache(File newFileOrFolder) {
        List<File> cachedFilesAndFolder = CacheService.getAllFilesAndFolders();
        if (cachedFilesAndFolder != null) {
            List<File> updatedFilesAndFolders = new ArrayList<>(cachedFilesAndFolder);
            updatedFilesAndFolders.add(newFileOrFolder);
            CacheService.updateAllFilesAndFolders(updatedFilesAndFolders);
        }
    }

    /**
     * The method assumes that the folder does not exist and that its parent exists.
     */
    private Xor<DriveError, File> createFolderNoIntermediate(String remotePath) {
        List<String> folderPathList = pathToList(remotePath);
        if (folderPathList.size() == 0) {
            return getRootFolder();
        } else if (folderPathList.size() == 1) {
            String folderName = folderPathList.get(folderPathList.size() - 1);
            return getRootFolder().flatMapRight(rootFolder -> createFolder(new File().setName(folderName), rootFolder));
        } else {
            String folderName = folderPathList.get(folderPathList.size() - 1);
            String joinedPath = SystemUtils.joinStrings(folderPathList.subList(0, folderPathList.size() - 1), "/", "/");
            return getFile(joinedPath)
                .flatMapRight(parent -> createFolder(new File().setName(folderName), parent));
        }
    }

    /**
     * Creates the folder with all the intermediate folders
     */
    private Xor<DriveError, File> createFolderWithIntermediate(String remotePath) {
        List<String> pathListToFolder = Arrays.asList(remotePath.split("/"));
        if (SystemUtils.isEmptyCollection(pathListToFolder))
            return getRootFolder();

        String pathToVisit = "/";
        Xor<DriveError, List<File>> xorFilePath = Xor.left(new DriveError("getLastFolderCreateIfNotExists initial value"));
        for (String currentFolder : pathListToFolder) {

            pathToVisit += currentFolder + "/";
            xorFilePath = getFilePathList(pathToVisit);

            if (xorFilePath.isLeft() && DriveErrorType.FOLDER_NOT_FOUND.equals(xorFilePath.getLeft().getType())) {
                Xor<DriveError, File> xorCreatedFolder = createFolderNoIntermediate(pathToVisit);
                if (xorCreatedFolder.isLeft())
                    return xorCreatedFolder;
                else
                    xorFilePath = getFilePathList(pathToVisit);
            } else if (xorFilePath.isLeft() && !DriveErrorType.FOLDER_NOT_FOUND.equals(xorFilePath.getLeft().getType())) {
                return Xor.left(xorFilePath.getLeft());
            }
        }

        return xorFilePath.mapRight(fileList -> fileList.get(fileList.size() - 1));
    }

    /**
     * Creates a folder inside its specified parent
     */
    private Xor<DriveError, File> createFolder(File folder, File parent) {
        folder.setMimeType("application/vnd.google-apps.folder");
        folder.setParents(Collections.singletonList(parent.getId()));
        return createAuthorizedDriveService()
            .mapLeft(authError -> new DriveError("Authorization error while trying to create a folder", DriveErrorType.NESTED).addNestedError(authError))
            .flatMapRight(driveService -> Xor.catchNonFatal(() -> driveService.files().create(folder).setFields("id, kind, mimeType, name, parents").execute()).mapLeft(DriveError::new))
            .mapRight(file -> {
                updateFilesAndFoldersCache(file);
                return file;
            });
    }

    private Xor<IOError, GoogleAuthorizationCodeFlow> createAuthorizationFlow() {
        return Xor.catchNonFatal(
            () -> new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, CLIENT_SECRETS, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline")
                .build()).mapLeft(IOError::new);
    }

    private Xor<AuthorizationError, Credential> authorize() {
        Credential cachedCredentials = CacheService.getCredentials();
        if (cachedCredentials != null) {
            return Xor.right(cachedCredentials);
        } else {
            logger.debug("Attempting to authorize");
            return createAuthorizationFlow().mapLeft(ioError -> new AuthorizationError("Error while attempting to authorize").addNestedError(ioError))
                .flatMapRight(flow -> Xor.catchNonFatal(() -> new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user")).mapLeft(AuthorizationError::new))
                .mapRight(credential -> {
                    logger.debug("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
                    CacheService.updateCredentials(credential);
                    return credential;
                });
        }
    }

    private Xor<AuthorizationError, Drive> createAuthorizedDriveService() {
        return authorize().mapRight(credential -> new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(CoreConfig.CONFIG.getString("goverdrive.name")).build());
    }

    private static Xor<DriveError, File> findConjunctFile(List<File> files, List<File> parents) {
        Optional<File> conjunctFile = Optional.empty();
        for (File file : files) {
            for (File parent : parents) {
                if (file.getParents().contains(parent.getId())) {
                    if (conjunctFile.isPresent()) {
                        return Xor.left(new DriveError("Duplicate folder: " + file.getName(), DriveErrorType.DUPLICATE_FOLDER));
                    } else {
                        conjunctFile = Optional.of(file);
                    }
                }
            }
        }
        if (conjunctFile.isPresent()) {
            return Xor.right(conjunctFile.get());
        } else {
            return Xor.left(new DriveError("No parent found for folder!", DriveErrorType.INVALID_PARENT));
        }
    }

    private static <T> List<T> addFirst(T elem, List<T> list) {
        List<T> copy = new ArrayList<>(list);
        copy.add(0, elem);
        return copy;
    }

    private static List<String> pathToList(String path) {
        List<String> pathList = new ArrayList<>();
        for (Path individualPath : Paths.get(path)) {
            pathList.add(individualPath.toString());
        }
        return pathList;
    }

    private static Xor<IOError, FileContent> loadFileContent(java.io.File localFile) {
        return Xor.catchNonFatal(() -> new FileContent(Files.probeContentType(localFile.toPath()), localFile)).mapLeft(IOError::new);
    }
}
