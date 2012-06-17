/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.watch.remote;

import java.util.Map.Entry;
import org.syncany.config.Config;
import org.syncany.config.Profile;
import org.syncany.connection.plugins.TransferManager;
import org.syncany.db.CloneClient;
import org.syncany.db.CloneFile;
import org.syncany.db.DatabaseHelper;
import org.syncany.exceptions.RemoteFileNotFoundException;
import org.syncany.repository.files.RemoteFile;
import org.syncany.exceptions.StorageException;
import org.syncany.repository.UpdateList;
import org.syncany.repository.files.UpdateFile;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.repository.files.ImageFile;
import org.syncany.repository.files.ProfileFile;
import org.syncany.repository.files.RepositoryFile;
import org.syncany.repository.files.StructuredFileList;

/**
 * Does periodical checks on the online storage, and applies them locally.
 *
 * <p>This currently includes the following steps:
 * <ul>
 * <li>List files, identify available update files
 * <li>Download required update files
 * <li>Identify conflicts and apply updates/changes
 * <li>Create local update files
 * <li>Upload local update file
 * <li>Delete old update files online
 * </ul>
 * 
 * Unlike the {@link StorageManager}, this class uses its own {@link TransferManager}
 * to be able to synchronously wait for the storage.
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class RemoteWatcher {
    private static final Logger logger = Logger.getLogger(RemoteWatcher.class.getSimpleName());
    
    private static final int INTERVAL = 10000;
    private static final boolean DEBUG_GZIP_AND_ENCRYPT_UPDATE_FILES = false;

    private Config config;
    private DatabaseHelper db;    
    
    private Profile profile;   
    
    private ChangeManager changeManager;
    private Timer timer;

    private Map<String, RemoteFile> remoteFileList;
    private StructuredFileList fileList;
    private UpdateList updateList;
    private TransferManager transfer;
    
    // TODO this should be in the DB cached somewhere.
    private Long lastFileVersionCount;
        
    // TODO this should be in the DB cached somewhere.
    private Date lastRepoFileUpdate;
    
    // TODO this should be in the DB cached somewhere.
    private Date lastUpdateFileDate;
    
    // TODO this should be in the DB cached somewhere.
    private Date lastLocalProfileFileUpdate;
    
    // TODO this should be in the DB cached somewhere.
    private Date lastLocalImageFileUpdate;


    public RemoteWatcher(Profile profile) {       
        this.profile = profile;

        this.changeManager = new ChangeManager(profile);
        this.timer = null;
        this.lastFileVersionCount = null;

        // cp. start()
        this.config = null;
        this.db = null;
        
        // cp. doUpdateCheck()
        this.remoteFileList = null;
        this.updateList = null;
        this.transfer = null;
    }

    public synchronized void start() {        
        // Dependencies
        if (config == null) {
            config = Config.getInstance();
            db = DatabaseHelper.getInstance();
        }
        
        // Reset connection
        reset();
        
        timer = new Timer("RemoteWatcher");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { doUpdateCheck(); } }, 0, INTERVAL);

        changeManager.start();
    }

    public synchronized void stop() {
        if (timer == null) {
            return;
        }

        changeManager.stop();

        timer.cancel();
        timer = null;
    }

    private void reset() {
        
        transfer = profile.getRepository().getConnection().createTransferManager();
        updateList = new UpdateList(profile);
    }

    private void doUpdateCheck() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("STARTING PERIODIC UPDATE CHECK ...");
        }

        reset();

        try {
            updateFileList();
            
            updateRepository();
            commitRepository();
            
            // 1. download update files
            downloadUpdates();

            // 3. Analyzing updates & looking for conflicts
            processUpdates();

            // 4. Create and upload local updates ///////
            commitLocalUpdateFile();
            
            // Profiles 
            commitLocalProfile();
            updateRemoteProfiles();

            // Images
            commitLocalImage();           
            updateRemoteImages();

            // 5. Delete old updates (only mine!) ///////
            deleteOldUpdateFiles();
            deleteOldProfileFiles();
            deleteOldImageFiles();
        }
        catch (StorageException ex) {
            logger.log(Level.WARNING, "Update check failed. Trying again in a couple of seconds.", ex);
        }
        finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("DONE WITH PERIODIC UPDATE CHECK ...");
            }

            try { transfer.disconnect(); }
            catch (StorageException ex) { /* Fressen! */ }
        }
    
    }

    private void updateFileList() throws StorageException {
        remoteFileList = transfer.list();
        fileList = new StructuredFileList(profile.getRepository(), remoteFileList);
    }
    
    
    private void updateRepository() throws StorageException {
        RepositoryFile repoFile = fileList.getNewestRepositoryFile();
        
        if (repoFile == null) {
            throw new StorageException("Unable to find repository-* file.");
        }
        
        // Are we already up-to-date?
        if (lastRepoFileUpdate != null && !repoFile.getLastUpdate().after(lastRepoFileUpdate)) {
            return;
        }
        
        // Do download and read it!
        try {            
            profile.getRepository().update(transfer, fileList);
            lastRepoFileUpdate = repoFile.getLastUpdate();
        }
        catch (Exception e) {
            throw new StorageException(e);
        }
    }   
    
    private void commitRepository() throws StorageException {
        RepositoryFile repoFile = fileList.getNewestRepositoryFile();
        
        if (repoFile == null) {
            throw new StorageException("Unable to find repository-* file.");
        }
        
        // Are we already up-to-date?
        if (!profile.getRepository().isChanged()) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("repository has not changed locally. No need to upload.");
            }
            
            return;
        }
        
        // Upload
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Uploading changed repository file ...");
        }
 
        try {      
            profile.getRepository().commit(transfer, fileList, false);
        }
        catch (Exception e) {
            throw new StorageException(e);
        }
    }       

    private void downloadUpdates() throws StorageException {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("2. Downloading update lists ...");
        }
        
        // Find newest client update files
        Collection<UpdateFile> newestUpdateFiles = fileList.getRemoteUpdateFiles().values();
        
        for (UpdateFile updateFile : newestUpdateFiles) {
            // Get client from DB (or create it!)
            CloneClient client = db.getClient(profile, updateFile.getMachineName(), true);            
            
            // Ignore if we are up-to-date
            if (client.getLastUpdate() != null && !updateFile.getLastUpdate().after(client.getLastUpdate())) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "   - Client ''{0}'' is up-to-date", updateFile.getMachineName());
                }
                
                continue;
            }
            
            try {
                // Download update file
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "   - Downloading update for ''{0}'' ...", client.getMachineName());
                }

                File tempUpdateFile = config.getCache().createTempFile("update-" + client.getMachineName());
                transfer.download(updateFile, tempUpdateFile);

                // Read & delete update file
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "     --> Parsing update for ''{0}'' ...", client.getMachineName());
                }

                updateFile.read(tempUpdateFile, DEBUG_GZIP_AND_ENCRYPT_UPDATE_FILES);
                tempUpdateFile.delete();

                // Add to update manager
                updateList.addRemoteUpdateFile(client, updateFile);
            }
            catch (Exception ex) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Reading update file of client {0} failed. Skipping update check.", client.getMachineName());
                }
                
                throw new StorageException(ex);
            }
        }
    }

    private void processUpdates() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("3a. Analyzing updates & looking for conflicts ...");
        }

        // Let the change manager do the actual work!
        changeManager.queueUpdates(updateList);


        // TODO should the changes be synchronous?
        // TODO because setting the clients' lastUpdate value assumes that the change mgr doesnt crash
        
        // Update last-updated date of clients
        if (logger.isLoggable(Level.INFO)) {
            logger.info("3b. Updating client DB entries ...");
        }

        for (Map.Entry<CloneClient, UpdateFile> e : updateList.getRemoteUpdateFiles().entrySet()) {
            CloneClient client = e.getKey(); 
            UpdateFile updateFile  = e.getValue();

            client.setLastUpdate(updateFile.getLastUpdate());
            client.merge();
        }
    }

    private void commitLocalUpdateFile() throws StorageException {
        // Check if new update file needs to be created/uploaded
        Long fileVersionCount = db.getFileVersionCount();

        if (fileVersionCount != null && lastFileVersionCount != null && fileVersionCount.equals(lastFileVersionCount)) {
            logger.info("4. No local changes. Skipping step upload.");
            return;
        }

        if (fileVersionCount == null || fileVersionCount == 0) {
            logger.warning("4. Nothing in DB. Skipping step upload.");
            return;
        }

        // Start making/uploading the file
        lastUpdateFileDate = new Date();
        lastFileVersionCount = fileVersionCount;

        File localUpdateFile = null;	
        UpdateFile remoteUpdateFile = new UpdateFile(profile.getRepository(), config.getMachineName(), lastUpdateFileDate);

        try {
            // Make temp. update file
            localUpdateFile = config.getCache().createTempFile("update-"+config.getMachineName());
            logger.info("4. Writing local changes to '"+localUpdateFile+"' ...");

            List<CloneFile> updatedFiles = db.getHistory(profile);
            remoteUpdateFile.setVersions(updatedFiles);
            remoteUpdateFile.write(localUpdateFile, DEBUG_GZIP_AND_ENCRYPT_UPDATE_FILES);

            // Upload
            logger.info("  - Uploading file to temp. file '"+remoteUpdateFile.getName()+"' ...");
            transfer.upload(localUpdateFile, remoteUpdateFile);

            localUpdateFile.delete();
        }
        catch (IOException ex) {
            if (localUpdateFile != null) {
                localUpdateFile.delete();
            }

            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    // Upload new local profile (if changed)
    private void commitLocalProfile() throws StorageException {        
        ProfileFile localNewestProfileFile = fileList.getNewestProfileFile();
        
        // Skip if no update needed
        if (!(localNewestProfileFile == null || lastLocalProfileFileUpdate == null ||
                localNewestProfileFile.getLastUpdate().after(lastLocalProfileFileUpdate))) {
            
            return;
        }
        
        Date newDate = new Date();
        ProfileFile localProfileFile = new ProfileFile(profile.getRepository(), config.getMachineName(), newDate);

        // Set public user details
        localProfileFile.setUserName(config.getUserName());

        // Save to temp file and upload
        try {
            File tempLocalProfile = config.getCache().createTempFile("profile-" + config.getMachineName());
            localProfileFile.write(tempLocalProfile);
            transfer.upload(tempLocalProfile, localProfileFile);
            tempLocalProfile.delete();

            // Delete old file
            if (localNewestProfileFile != null) {
                transfer.delete(localNewestProfileFile);
            }

            // Update 
            lastLocalProfileFileUpdate = newDate;
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "ERROR while uploading local profile.", e);
            // TODO do something
        }
    }
    
    private void updateRemoteProfiles() throws RemoteFileNotFoundException, StorageException {
        /// Download new user profiles
        Collection<ProfileFile> remoteProfileFiles = fileList.getRemoteProfileFiles().values();

        for (ProfileFile f : remoteProfileFiles) {
            CloneClient client = db.getClient(profile, f.getMachineName(), true);

            // Skip if old
            if (client.getLastProfileUpdate() != null && !f.getLastUpdate().after(client.getLastProfileUpdate())) {
                continue;
            }
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Downloading profile of client ''{0}'' ...", client.getMachineName());
            }

            try {
                File tempRemoteProfile = config.getCache().createTempFile(f.getName());
                transfer.download(f, tempRemoteProfile);
                f.read(tempRemoteProfile);

                // Apply and persist
                client.setLastProfileUpdate(new Date());
                client.setUserName(f.getUserName());
                client.merge();
            }
            catch (IOException e) {
                logger.log(Level.SEVERE, "ERROR while downloading remote profile of " + client.getMachineName() + ".", e);
                // TODO do something
            }            
        }
    }
    
    private void commitLocalImage() throws RemoteFileNotFoundException, StorageException {
        // Upload local image (if updated)
        ImageFile localNewestImageFile = fileList.getNewestImageFile();
        
        // Skip if no update needed
        if (!(localNewestImageFile == null || lastLocalImageFileUpdate == null ||
                localNewestImageFile.getLastUpdate().after(lastLocalImageFileUpdate))) {
            
            return;
        }
        
        return;
        /*
        Date newDate = new Date();
        ImageFile localImageFile = new ImageFile(profile.getRepository(), config.getMachineName(), newDate);
        
        if (lastLocalImageFileUpdate == null || localNewestImageFile == null || localNewestImageFile.getLastUpdate().after(lastLocalImageFileUpdate)) {
            // If 'No Image' is selected, OR (!) if the local image file is not there
            if (config.getUserImageType() == Config.UserImageType.None
                    || config.getUserImageFile() == null || !config.getUserImageFile().exists()) {

                if (localNewestImageFile != null) {
                    transfer.delete(localNewestImageFile);
                }

                lastLocalImageFileUpdate = new Date();
            } else {
                try {
                    File originalImageFile = null;

                    if (config.getUserImageType() == Config.UserImageType.System) {
                        originalImageFile = config.getCache().createTempFile("image-" + config.getMachineName());
                        Image image = ImageUtil.getScaledImage(config.getUserImage(), Constants.PROFILE_IMAGE_MAX_WIDTH, Constants.PROFILE_IMAGE_MAX_HEIGHT);
                        ImageIO.write(ImageUtil.toBufferedImage(image), "png", originalImageFile);
                    }
                    else if (config.getUserImageType() == Config.UserImageType.Other) {
                        originalImageFile = config.getUserImageFile();
                    }

                    // TODO encrypt

                    Date newDate = new Date();
                    transfer.upload(originalImageFile, new ImageFile(profile.getRepository(), config.getMachineName(), newDate));

                    // Update cached value
                    lastLocalImageFileUpdate = newDate;
                } catch (IOException e) {
                    // TODO do something
                    logger.log(Level.WARNING, "Error while uploading local image.", e);
                }
            }
        }*/
    }
    
    private void updateRemoteImages() {
        // Download new user images (if changed)
        Map<String, ImageFile> remoteImageFiles = fileList.getRemoteImageFiles();

        for (ImageFile f : remoteImageFiles.values()) {
            CloneClient client = db.getClient(profile, f.getMachineName(), true);

            // Skip if old
            if (client.getLastImageUpdate() != null && !f.getLastUpdate().after(client.getLastImageUpdate())) {
                continue;
            }
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Downloading image of client ''{0}'' ...", client.getMachineName());
            }
            
            // Download 
            try {
                File tempRemoteImage = config.getCache().createTempFile(f.getName());
                transfer.download(f, tempRemoteImage);

                // TODO decrypt

                tempRemoteImage.renameTo(client.getUserImageFile());

                // Apply and persist
                client.setLastImageUpdate(new Date());
                client.merge();
            }
            catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "ERROR while downloading remote image of " + client.getMachineName() + ".", e);
                }
            }
        }
    }

    private void deleteOldUpdateFiles() {
        TreeMap<Long, UpdateFile> localUpdatesMap = fileList.getLocalUpdateFiles();
        
        while (localUpdatesMap.size() > 1) {
            Entry<Long, UpdateFile> firstEntry = localUpdatesMap.pollFirstEntry();            

            try {
                transfer.delete(firstEntry.getValue());            
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Could not delete old update file", e);
            }
        }
    }
    
    private void deleteOldProfileFiles() {
        TreeMap<Long, ProfileFile> localUpdatesMap = fileList.getLocalProfileFiles();
        
        while (localUpdatesMap.size() > 1) {
            Entry<Long, ProfileFile> firstEntry = localUpdatesMap.pollFirstEntry();            

            try {
                transfer.delete(firstEntry.getValue());            
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Could not delete old profile file", e);
            }
        }
    }
  
    private void deleteOldImageFiles() {
        TreeMap<Long, ImageFile> localUpdatesMap = fileList.getLocalImageFiles();
        
        while (localUpdatesMap.size() > 1) {
            Entry<Long, ImageFile> firstEntry = localUpdatesMap.pollFirstEntry();            

            try {
                transfer.delete(firstEntry.getValue());            
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Could not delete old image file", e);
            }
        }
    }

}
