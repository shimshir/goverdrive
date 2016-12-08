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

import de.admir.goverdrive.core.error.DriveError;
import de.admir.goverdrive.core.error.AuthorizationError;
import de.admir.goverdrive.core.error.IOError;
import de.admir.goverdrive.core.util.SystemUtils;
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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import static de.admir.goverdrive.core.error.DriveError.*;

public class GoverdriveServiceImpl implements GoverdriveService {
    private static final Logger LOG = LoggerFactory.getLogger(GoverdriveServiceImpl.class);

    private static final String APPLICATION_NAME = "Goverdrive";

    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".credentials/" + APPLICATION_NAME);

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);

    private static final HttpTransport HTTP_TRANSPORT = SystemUtils.handleFatal(
			 GoogleNetHttpTransport::newTrustedTransport,
			 e -> LOG.error(MarkerFactory.getMarker("FATAL"), "Could not instantiate HTTP_TRANSPORT", e)
	 );

    private static final FileDataStoreFactory DATA_STORE_FACTORY = SystemUtils.handleFatal(
			 () -> new FileDataStoreFactory(DATA_STORE_DIR),
			 e -> LOG.error(MarkerFactory.getMarker("FATAL"), "Could not instantiate DATA_STORE_FACTORY", e)
	 );

    private static final GoogleClientSecrets CLIENT_SECRETS = SystemUtils.handleFatal(
			 () -> GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(GoverdriveServiceImpl.class.getResourceAsStream("/client_creds.json"))),
			 e -> LOG.error(MarkerFactory.getMarker("FATAL"), "Could not instantiate CLIENT_SECRETS", e)
	 );

    @Override
    public OutputStream getFileStream(String path) {
        /*Drive driveService = createAuthorizedDriveService();
        OutputStream outputStream = new ByteArrayOutputStream();
        driveService.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream);*/
        return null;
    }

	@Override
	public Xor<DriveError, File> createFile(String localPath, String remotePath) {
		final java.io.File localFile = new java.io.File(localPath);
		Xor<IOError, FileContent> xorFileContent = getFileContent(localFile);
		return xorFileContent.mapLeft(ioError -> new DriveError("asd", DriveErrorType.NESTED).addNestedError(ioError)).flatMapRight(fileContent -> {

			List<String> filePath = pathToList(remotePath);

			Xor<DriveError, File> xorRemoteFolder = filePath.size() == 0 ?
					Xor.left(new DriveError("You must provide at least a file name", DriveErrorType.ILLEGAL_ARGUMENTS)) :
					getLastFolderCreateIfNotExists(filePath.subList(0, filePath.size() - 1));

			return xorRemoteFolder.flatMapRight(folder -> createAuthorizedDriveService().mapLeft(authError -> new DriveError("asd", DriveErrorType.NESTED).addNestedError(authError))
					.flatMapRight(driveService -> Xor.catchNonFatal(() -> {
						File fileMetadata = new File();
						fileMetadata.setName(localFile.getName());
						fileMetadata.setParents(Collections.singletonList(folder.getId()));
						return driveService.files().create(fileMetadata, fileContent).setFields("id, parents").execute();
					}).mapLeft(e -> new DriveError("asd", DriveErrorType.NESTED).addNestedError(new IOError(e)))));
		});
	}

    @Override
    public Xor<DriveError, File> getRootFolder() {
        return createAuthorizedDriveService()
              .mapLeft(authError -> new DriveError("Error while creating authorized drive service", DriveErrorType.NESTED).addNestedError(authError))
              .flatMapRight(driveService -> Xor.catchNonFatal(() ->
                          driveService
                                .files()
                                .get("root")
                                .execute()
                    ).mapLeft(DriveError::new)
              );
    }

    @Override
    public Xor<DriveError, List<File>> getAllFilesAndFolders() {
        return createAuthorizedDriveService()
              .mapLeft(authError -> new DriveError("Error while creating authorized drive service", DriveErrorType.NESTED).addNestedError(authError))
              .flatMapRight(driveService -> Xor.catchNonFatal(() ->
                          driveService
                                .files()
                                .list()
                                .setFields("files (id, name, parents, mimeType)")
                                .execute()
                                .getFiles()
                    ).mapLeft(DriveError::new)
              ).flatMapRight(files -> getRootFolder().mapRight(rootFolder -> addFirst(rootFolder, files)));
    }

    @Override
    public Xor<DriveError, File> findFile(String remotePath) {
		 return getFilePath(remotePath).mapRight(files -> files.get(files.size() - 1));
	 }

    @Override
    public Xor<DriveError, List<File>> getFilePath(String remotePath) {
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
                Xor<DriveError, File> xorConjunctFile = getConjunctFile(files, possibleParents);
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

	/**
	 * The method assumes that the folder does not exist and that its parent exists.
	 * @param remotePath
	 * @return
	 */
	@Override
	public Xor<DriveError, File> createFolder(String remotePath) {
		List<String> folderPathList = pathToList(remotePath);
		if (folderPathList.size() == 0) {
			return getRootFolder();
		}
		else if (folderPathList.size() == 1) {
			String folderName = folderPathList.get(folderPathList.size() - 1);
			return getRootFolder()
					.flatMapRight(rootFolder -> createFolder(new File().setName(folderName), rootFolder));
		} else {
			String folderName = folderPathList.get(folderPathList.size() - 1);
			return findFile('/' + StringUtils.join(folderPathList.subList(0, folderPathList.size() - 1), '/'))
					.flatMapRight(parent -> createFolder(new File().setName(folderName), parent));
		}
	}

	private Xor<DriveError, File> createFolder(File folder, File parent) {
		folder.setMimeType("application/vnd.google-apps.folder");
		folder.setParents(Collections.singletonList(parent.getId()));

		return createAuthorizedDriveService().mapLeft(authError -> new DriveError("asd", DriveErrorType.NESTED).addNestedError(authError))
				.flatMapRight(driveService ->
					Xor.catchNonFatal(() -> driveService
							.files()
							.create(folder)
							.setFields("id")
							.execute()).mapLeft(DriveError::new)
				);
	}

    private Xor<IOError, GoogleAuthorizationCodeFlow> createAuthorizationFlow() {
        return Xor.catchNonFatal(() ->
                new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, CLIENT_SECRETS, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline").build()
        ).mapLeft(IOError::new);
    }

    private Xor<AuthorizationError, Credential> authorize() {
        LOG.debug("Attempting to authorize");
        return createAuthorizationFlow()
                .mapLeft(ioError -> new AuthorizationError("Error while attempting to authorize").addNestedError(ioError))
                .flatMapRight(flow -> Xor.catchNonFatal(() -> new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user"))
                        .mapLeft(AuthorizationError::new))
                .unit(() -> LOG.debug("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath()));
    }

    private Xor<AuthorizationError, Drive> createAuthorizedDriveService() {
        return authorize()
                .mapRight(credential -> new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build());
    }

    private static Xor<DriveError, File> getConjunctFile(List<File> files, List<File> parents) {
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

	private Xor<DriveError, File> getLastFolderCreateIfNotExists(List<String> pathListToFolder) {
		if (CollectionUtils.isEmpty(pathListToFolder))
			return getRootFolder();

		String pathToVisit = "/";
		Xor<DriveError, List<File>> xorFilePath = Xor.left(new DriveError("asd initial"));
		for (String currentFolder : pathListToFolder) {

			pathToVisit += currentFolder + "/";
			xorFilePath = getFilePath(pathToVisit);

			if (xorFilePath.isLeft() && DriveErrorType.FOLDER_NOT_FOUND.equals(xorFilePath.getLeft().getType())) {
				Xor<DriveError, File> xorCreatedFolder = createFolder(pathToVisit);
				if (xorCreatedFolder.isLeft())
					return xorCreatedFolder;
				else
					xorFilePath = getFilePath(pathToVisit);
			} else if (xorFilePath.isLeft() && !DriveErrorType.FOLDER_NOT_FOUND.equals(xorFilePath.getLeft().getType())) {
				return Xor.left(xorFilePath.getLeft());
			}
		}

		return xorFilePath.mapRight(fileList -> fileList.get(fileList.size() - 1));
	}

	private Xor<IOError, FileContent> getFileContent(java.io.File localFile) {
		return Xor.catchNonFatal(() -> new FileContent(Files.probeContentType(localFile.toPath()), localFile)).mapLeft(IOError::new);
	}
}
