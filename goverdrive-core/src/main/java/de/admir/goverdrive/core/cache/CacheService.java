package de.admir.goverdrive.core.cache;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.model.File;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CacheService {
   private static final String CREDENTIALS_KEY = "credentials";
   private static final String ROOT_FOLDER_KEY = "rootFolder";
   private static final String ALL_FILES_AND_FOLDERS_KEY = "allFilesAndFoldersCache";

   private CacheService() {
   }

   private static final Cache<String, Credential> credentialsCache = CacheBuilder.newBuilder().maximumSize(1).expireAfterAccess(30, TimeUnit.MINUTES).build();
   private static final Cache<String, File> rootFolderCache = CacheBuilder.newBuilder().maximumSize(1).expireAfterAccess(60, TimeUnit.MINUTES).build();
   private static final Cache<String, List<File>> allFilesAndFoldersCache = CacheBuilder.newBuilder().maximumSize(1).expireAfterAccess(10, TimeUnit.SECONDS).build();

   public static Credential getCredentials() {
      return credentialsCache.getIfPresent(CREDENTIALS_KEY);
   }

   public static File getRootFolder() {
      return rootFolderCache.getIfPresent(ROOT_FOLDER_KEY);
   }

   public static List<File> getAllFilesAndFolders() {
      return allFilesAndFoldersCache.getIfPresent(ALL_FILES_AND_FOLDERS_KEY);
   }

   public static void updateCredentials(Credential credential) {
      credentialsCache.put(CREDENTIALS_KEY, credential);
   }

   public static void updateRootFolder(File rootFolder) {
      rootFolderCache.put(ROOT_FOLDER_KEY, rootFolder);
   }

   public static void updateAllFilesAndFolders(List<File> files) {
      allFilesAndFoldersCache.put(ALL_FILES_AND_FOLDERS_KEY, files);
   }

   public static void clearCredentials() {
      credentialsCache.invalidateAll();
   }

   public static void clearRootFolder() {
      rootFolderCache.invalidateAll();
   }

   public static void clearAllFilesAndFolders() {
      allFilesAndFoldersCache.invalidateAll();
   }
}
