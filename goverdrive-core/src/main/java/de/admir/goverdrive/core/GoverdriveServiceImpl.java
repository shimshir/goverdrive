package de.admir.goverdrive.core;

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

import de.admir.goverdrive.core.error.ApiError;
import de.admir.goverdrive.core.error.AuthorizationError;
import de.admir.goverdrive.core.error.IOError;
import de.admir.goverdrive.core.util.Xor;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GoverdriveServiceImpl implements GoverdriveService {
    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    private static final String APPLICATION_NAME = "Goverdrive";

    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".credentials/" + APPLICATION_NAME);

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static FileDataStoreFactory DATA_STORE_FACTORY;

    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);

    private static HttpTransport HTTP_TRANSPORT;

    private static GoogleClientSecrets CLIENT_SECRETS;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
            CLIENT_SECRETS = GoogleClientSecrets.load(JSON_FACTORY,
                    new InputStreamReader(
                            GoverdriveServiceImpl.class.getResourceAsStream("/client_creds.json")
                    )
            );
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private Xor<IOError, GoogleAuthorizationCodeFlow> createAuthorizationFlow() {
        return Xor.catchNonFatal(() ->
                new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, CLIENT_SECRETS, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline").build()
        ).mapLeft(IOError::new);
    }

    private Xor<AuthorizationError, Credential> authorize() {
        return createAuthorizationFlow()
                .mapLeft(ioError -> new AuthorizationError("Error while attempting to authorize").addNestedError(ioError))
                .flatMapRight(flow -> Xor.catchNonFatal(() -> new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user"))
                        .mapLeft(AuthorizationError::new))
                .unit(() -> LOG.info("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath()));
    }

    private Xor<AuthorizationError, Drive> createAuthorizedDriveService() {
        return authorize()
                .mapRight(credential -> new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build());
    }

    @Override
    public OutputStream getFileStream(String path) {
        /*Drive driveService = createAuthorizedDriveService();
        OutputStream outputStream = new ByteArrayOutputStream();
        driveService.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream);*/
        return null;
    }

    @Override
    public Xor<ApiError, File> createFile(String localPath, String remotePath) {
        String folderId = "0Bx4CTW5l9jV5V3NOZDlIdVlaTlU";
        File fileMetadata = new File();
        fileMetadata.setName("test3.txt");
        fileMetadata.setParents(Collections.singletonList(folderId));
        java.io.File localFile = new java.io.File("/home/admir/test.txt");

        return Xor.catchNonFatal(() -> new FileContent(Files.probeContentType(localFile.toPath()), localFile))
                .mapLeft(ioExc -> new ApiError("Error while probing content type!").addNestedError(new IOError(ioExc)))
                .flatMapRight(mediaContent -> createAuthorizedDriveService()
                        .mapLeft(authError -> new ApiError("Error while creating authorized drive service").addNestedError(authError))
                        .flatMapRight(driveService ->
                                Xor.catchNonFatal(() -> driveService.files().create(fileMetadata, mediaContent).setFields("id, parents").execute())
                                        .mapLeft(ApiError::new)
                        )
                );
    }

    @Override
    public Xor<ApiError, File> getRootFolder() {
        return createAuthorizedDriveService()
                .mapLeft(authError -> new ApiError("Error while creating authorized drive service").addNestedError(authError))
                .flatMapRight(driveService -> Xor.catchNonFatal(() ->
                                driveService
                                        .files()
                                        .get("root")
                                        .execute()
                        ).mapLeft(ApiError::new)
                );
    }

    @Override
    public Xor<ApiError, List<File>> getAllFilesAndFolders() {
        return createAuthorizedDriveService()
                .mapLeft(authError -> new ApiError("Error while creating authorized drive service").addNestedError(authError))
                .flatMapRight(driveService -> Xor.catchNonFatal(() ->
                                driveService
                                        .files()
                                        .list()
                                        .setFields("files (id, name, parents, mimeType)")
                                        .execute()
                                        .getFiles()
                        ).mapLeft(ApiError::new)
                ).flatMapRight(files -> getRootFolder().mapRight(rootFolder -> addFirst(rootFolder, files)));
    }

    @Override
    public Xor<ApiError, List<File>> getFilePath(String remotePath) {
        List<String> fileNames = pathToList(Paths.get(remotePath));

        final Xor<ApiError, List<File>> xorFiles = getAllFilesAndFolders();

        final Xor<ApiError, List<List<File>>> xorCandidatesList = xorFiles.flatMapRight(files -> {
            List<List<File>> candidatesList = new ArrayList<>();
            for (String fileName : addFirst("My Drive", fileNames)) {
                List<File> candidates = files.stream().filter(file -> fileName.equals(file.getName())).collect(Collectors.toList());
                if (candidates.size() == 0)
                    return Xor.left(new ApiError("No folder found named: " + fileName));
                candidatesList.add(candidates);
            }

            return Xor.right(candidatesList);
        });

        Xor<ApiError, List<File>> xorCandidates = xorCandidatesList.flatMapRight(candidatesList -> {
            LinkedList<File> filteredCandidates = new LinkedList<>();
            for (int candidatesIndex = candidatesList.size() - 1; candidatesIndex > 0; candidatesIndex--) {
                List<File> files = candidatesList.get(candidatesIndex);
                List<File> possibleParents = candidatesList.get(candidatesIndex - 1);
                Xor<ApiError, File> xorConjunctFile = getConjunctFile(files, possibleParents);
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
    public String createFolder(String path) {
        return null;
    }

    private static Xor<ApiError, File> getConjunctFile(List<File> files, List<File> parents) {
        Optional<File> conjunctFile = Optional.empty();
        for (File file : files) {
            for (File parent : parents) {
                if (file.getParents().contains(parent.getId())) {
                    if (conjunctFile.isPresent()) {
                        return Xor.left(new ApiError("Duplicate folder: " + file.getName()));
                    } else {
                        conjunctFile = Optional.of(file);
                    }
                }
            }
        }
        if (conjunctFile.isPresent()) {
            return Xor.right(conjunctFile.get());
        } else {
            return Xor.left(new ApiError("No parent found for folder!"));
        }
    }

    private static <T> List<T> addFirst(T elem, List<T> list) {
        List<T> copy = new ArrayList<>(list);
        copy.add(0, elem);
        return copy;
    }

    private static List<String> pathToList(Path path) {
        List<String> pathList = new ArrayList<>();
        for (Path individualPath : path) {
            pathList.add(individualPath.toString());
        }
        return pathList;
    }
}
