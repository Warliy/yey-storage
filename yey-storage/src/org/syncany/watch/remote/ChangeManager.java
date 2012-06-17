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

import java.io.IOException;
import org.syncany.config.Config;
import org.syncany.config.Profile;
import org.syncany.connection.plugins.Connection;
import org.syncany.connection.plugins.TransferManager;
import org.syncany.db.CloneChunk;
import org.syncany.db.CloneFile;
import org.syncany.db.CloneFile.Status;
import org.syncany.db.DatabaseHelper;
import org.syncany.exceptions.CouldNotApplyUpdateException;
import org.syncany.exceptions.StorageException;
import org.syncany.repository.UpdateList;
import org.syncany.repository.files.RemoteFile;
import org.syncany.repository.Update;
import org.syncany.util.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import org.syncany.Constants;
import org.syncany.config.Folder;
import org.syncany.db.CloneClient;
import org.syncany.db.CloneFile.SyncStatus;
import org.syncany.gui.desktop.Desktop;
import org.syncany.gui.tray.Tray;
import org.syncany.index.Indexer;
import org.syncany.repository.Uploader;

/**
 *
 * @author Philipp C. Heckel
 */
public class ChangeManager {
    private static final Logger logger = Logger.getLogger(ChangeManager.class.getSimpleName());
    private static final int INTERVAL = 5000;

    // cp start()
    private final DependencyQueue q;
    private Profile profile;
    private Timer timer;
    private TransferManager transfer;
    private EntityManager em;

    // deps
    private Config config;
    private DatabaseHelper db;
    private Tray tray;
    private Desktop desktop;
    private Indexer indexer;
    private Uploader uploader;

    public ChangeManager(Profile profile) {
        this.profile = profile;
        this.q = new DependencyQueue();

        // cmp. start()
        this.timer = null;
    }

    public synchronized void start() {
        // Dependencies
        if (config == null) {
            config = Config.getInstance();
            db = DatabaseHelper.getInstance();
            tray = Tray.getInstance();
            desktop = Desktop.getInstance();
            indexer = Indexer.getInstance();
        }

        if (timer != null) {
            return;
        }

        transfer = profile.getRepository().getConnection().createTransferManager();
        uploader = profile.getUploader();

        timer = new Timer("ChangeMgr");
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                doProcessUpdates();
            }
        }, 0, INTERVAL);
    }

    public synchronized void stop() {
        if (timer == null) {
            return;
        }

        timer.cancel();
        timer = null;
    }

    public void queueUpdates(UpdateList ul) {
        synchronized (q) {
            q.addAll(ul.generateUpdateList());
        }
    }

    private void doProcessUpdates() {
        // Note: Don't do this in the init method.
        //       This MUST happen in this thread!
        em = config.getDatabase().getEntityManager();

        Update update = null;
        Map<Long, List<Update>> newUpdatesMap = new HashMap<Long, List<Update>>();

        //q.printMaps();
        boolean firedProcessingStartEvent = !q.isEmpty();

        if (firedProcessingStartEvent) {
            tray.setStatusIcon(Tray.StatusIcon.UPDATING);
        }

        while (null != (update = q.poll())) {
            //logger.info("Processing update "+update+" ...");

            CloneFile existingVersion = db.getFileOrFolder(profile, update.getFileId(), update.getVersion());

            boolean isLocalConflict = isLocalConflict(existingVersion, update);


            ///Existing version equals update -> skip: file is up-to-date!
            if (existingVersion != null && !isLocalConflict) {
                logger.log(Level.INFO, "- File {0}, version {1} is UP-TO-DATE. ", new Object[]{update.getFileId(), update.getVersion()});
                continue;
            }

            
            logger.info("- Processing update: " + update);
            if (!newUpdatesMap.containsKey(update.getFileId())) {
                newUpdatesMap.put(update.getFileId(), new ArrayList<Update>());
            }
            
            newUpdatesMap.get(update.getFileId()).add(update);

            ///// 3. Handle all possible cases

            CloneFile localVersionById = db.getFileOrFolder(profile, update.getFileId());

            // A) I know the file ID
            if (localVersionById != null) {
                /// a) Conflict exists
                if (isLocalConflict) {
                    logger.info("Aa) File ID " + update.getFileId() + " known, conflict found of " + existingVersion + " with updates " + update + ". Resolving conflict ...");
                    resolveConflict(existingVersion, update);
                } 
                        
                /// b) No conflict exists (only apply new versions)
                else {
                    logger.info("Ab) File ID " + update.getFileId() + " known. New update found. Applying ...");
                    applyUpdate(localVersionById, update);
                }
            } 
            
            // B) I do not know the file ID
            else {
                Folder root = profile.getFolders().get(update.getRootId());

                if (root == null) {
                    // TODO given ROOT is unknown! 
                    logger.severe("TODO TODO TODO  --- ROOT ID " + update.getRootId() + " is unknown. ");
                    continue;
                }

                File localFileName = FileUtil.getCanonicalFile(new File(root.getLocalFile() + File.separator + update.getPath() + File.separator + update.getName()));
                CloneFile localVersionByFilename = db.getFileOrFolder(root, localFileName); //update.getRootId(), update.getPath(), update.getName());

                // a) No local file (in DB) exists: This one must be new!
                if (localVersionByFilename == null) {
                    logger.log(Level.INFO, "Ba) File ID {0} NOT known. No conflicting filename found in DB. Applying updates of new file ...", update.getFileId());
                    applyUpdate(null, update);
                } 
                        
                // b) Local file exists: 
                //    If the checksum doesn't match, this is a conflict
                //    If the checksum matches, the histories must be merged
                else {
                    logger.info("Bb) File ID " + update.getFileId() + " NOT known. Conflicting file (same file path) FOUND in DB: " + localVersionByFilename);

                    // Both cases: 
                    //   Winner is the client that started the file first (first version).
                    //   -> Compare first versions!
                    CloneFile localFirstVersion = localVersionByFilename.getFirstVersion();
                    CloneFile localLastVersion = localFirstVersion.getLastVersion();
                    
                    Date remoteFirstUpdateDate;
                    
                    if (update.getVersion() == 1) {
                        remoteFirstUpdateDate = update.getUpdated();
                    }
                    else {
                        CloneFile remoteFirstVersion = db.getFileOrFolder(profile, update.getFileId(), 1);
                        
                        if (remoteFirstVersion == null) {
                            throw new RuntimeException("Inconsistent database. File "+update.getFileId()+", version 1 should exist in the DB.");
                        }
                        
                        remoteFirstUpdateDate = remoteFirstVersion.getUpdated();
                    }

                    boolean isLocalWinner = localFirstVersion.getUpdated().before(remoteFirstUpdateDate);

                    // Double check on file system
                    if (!localLastVersion.getFile().exists()) {
                        logger.warning("Bb) WARNING: Local file " + localLastVersion.getFile() + " does not exist. Indexer late?");
                        logger.warning("Bb) TODO TODO TODO -- re-add updates to the Q ...");
                        
                        indexer.queueDeleted(root, localLastVersion.getFile());
                        
                        try { Thread.sleep(1000); }
                        catch (InterruptedException e) { }
                        
                        q.add(update);
                        continue;
                    }

                    // i) Checksum equals: Merge the histories; the loser adds a MERGED to his history.
                    if (localVersionByFilename.getChecksum() == update.getChecksum()) {
                        CloneFile updateVersion = addToDB(update);
                        
                        // 1) Local client won (other client must give up his history):
                        //    If there is not MERGED version yet, add MERGED version to remote history
                        if (isLocalWinner) {
                            updateVersion.setSyncStatus(SyncStatus.UPTODATE);
                            updateVersion.merge();
                         /*   // a) Last update version is a MERGED version, do nothing (but add it to the DB)
                            if (update.getStatus() == Status.MERGED) {
                                logger.info("Bbi1a) Local file has same checksum, but last update is a MERGED version. Adding to DB.");
                                addToDB(update);
                            } 
                            
                            // b) Merge REMOTE version with my local one.
                            else {
                                logger.info("Bbi1b) Local file has same checksum, MERGING histories. Local version WINS ... ");

                                // Add updates to DB
                                CloneFile remoteLastVersion = addToDB(update);

                                // Add remote 'merged' version			    
                                CloneFile remoteMergeVersion = (CloneFile) remoteLastVersion.clone();
                                remoteMergeVersion.setVersion(remoteLastVersion.getVersion() + 1);
                                remoteMergeVersion.setStatus(Status.MERGED);
                                remoteMergeVersion.setSyncStatus(SyncStatus.UPTODATE);
                                remoteMergeVersion.setMergedTo(localLastVersion);                                
                                remoteMergeVersion.updateVersionId();
                                remoteMergeVersion.persist();
                            }*/
                        } 
                                
                        // 2) Local client lost (must give up his history): add MERGED version to local history
                        else {
                            // a) Last update version is a MERGED version, do nothing (but add it to the DB)
                            if (update.getStatus() == Status.MERGED) {
                                logger.info("Bbi2a) Local file has same checksum, but last update is a MERGED version. Adding to DB.");
                                //addToDB(update);
                                updateVersion.persist();
                            } 
                            
                            // b) Merge local version with the remote one
                            else {
                                logger.info("Bbi2) Local file has same checksum, MERGING histories. Local version LOSES ... ");

                                // Add updates to DB
                                //CloneFile remoteLastVersion = addToDB(update);
                                updateVersion.setSyncStatus(SyncStatus.UPTODATE);
                                
                                // Add local 'merged' version			    
                                CloneFile localMergeVersion = (CloneFile) localLastVersion.clone();
                                localMergeVersion.setVersion(localLastVersion.getVersion() + 1);
                                localMergeVersion.setStatus(Status.MERGED);
                                localMergeVersion.setSyncStatus(SyncStatus.UPTODATE);
                                localMergeVersion.setMergedTo(updateVersion);
                                
                                db.merge(updateVersion, localMergeVersion);
                            }
                        }
                    } 
                            
                    // ii) Checksum not equal: Conflict; the loser moves his file to a "conflicted copy" version
                    else {
                        // 1) Local client won: do nothing?
                        if (isLocalWinner) {
                            logger.info("Bbii1) Local file has NOT the same checksum. Local version WINS. Making 'conflicting copy' for remote version ... ");

                            // Add to DB
                            CloneFile remoteLastVersion = addToDB(update);

                            // Download remote version			    
                            File tempLosingFile = null;
                            try {
                                tempLosingFile = File.createTempFile(".ignore-assemble-to-" + remoteLastVersion.getFile().getName() + "-", "", remoteLastVersion.getFile().getParentFile());
                            } catch (IOException ex) {
                                Logger.getLogger(ChangeManager.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            FileUtil.deleteRecursively(tempLosingFile); // just in case!

                            try {
                                // Download and assemble winning file
                                downloadChunks(remoteLastVersion);
                                assembleFile(remoteLastVersion, tempLosingFile);
                            } catch (CouldNotApplyUpdateException ex) {
                                logger.log(Level.SEVERE, "TODO TODO TODO TODO !!!!!!!! Unable to download/assemble winning file!", ex);
                                // TODO TODO TODO error handling!
                                continue;
                            }


                            //// Add remote 'conflicted' version			    

                            // Name
                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

                            String newFileName =
                                    FileUtil.getBasename(remoteLastVersion.getName())
                                    + " (" + update.getClientName()
                                    + (update.getClientName().endsWith("s") ? "'" : "'s")
                                    + " conflicting copy, "
                                    + dateFormat.format(new Date())
                                    + ")" + FileUtil.getExtension(remoteLastVersion.getName(), true);

                            // DB entry			    
                            CloneFile remoteConflictVersion = (CloneFile) remoteLastVersion.clone();
                            remoteConflictVersion.setName(newFileName);
                            remoteConflictVersion.setUpdated(new Date());
                            remoteConflictVersion.setVersion(remoteLastVersion.getVersion() + 1);
                            remoteConflictVersion.setStatus(Status.RENAMED); // TODO this could be a problem for the remote client!
                            remoteConflictVersion.setMergedTo(localLastVersion);
                            remoteConflictVersion.persist();

                            // File system
                            logger.info("Rename temp file to " + remoteLastVersion.getFile() + " ...");
                            tempLosingFile.renameTo(remoteConflictVersion.getFile());

                            // Update DB
                            updateSyncStatus(remoteConflictVersion, SyncStatus.UPTODATE);

                            // Notify uploader
                            uploader.queue(remoteConflictVersion);


                        }
                                
                        // 2) Local client lost: adjust history and move to "conflicted copy" version
                        else {
                            logger.info("Bbii2) Local file has NOT the same checksum. Local version LOSES. Making 'conflicted copy' version ... ");

                            // Add to DB
                            CloneFile lastRemoteVersion = addToDB(update);

                            // Download remote version			    
                            File tempWinningFile = null;
                            try {
                                tempWinningFile = File.createTempFile(".ignore-assemble-to-" + lastRemoteVersion.getFile().getName() + "-", "", lastRemoteVersion.getFile().getParentFile());
                            } catch (IOException ex) {
                                Logger.getLogger(ChangeManager.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            FileUtil.deleteRecursively(tempWinningFile); // just in case!

                            try {
                                // Download and assemble winning file
                                downloadChunks(lastRemoteVersion);
                                assembleFile(lastRemoteVersion, tempWinningFile);
                            } catch (CouldNotApplyUpdateException ex) {
                                logger.log(Level.SEVERE, "TODO TODO TODO TODO !!!!!!!! Unable to download/assemble winning file!", ex);
                                // TODO TODO TODO error handling!
                                continue;
                            }




                            //// Add local 'conflicted' version			    

                            // Name
                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

                            String newFileName =
                                    FileUtil.getBasename(localLastVersion.getName())
                                    + " (" + config.getMachineName()
                                    + (config.getMachineName().endsWith("s") ? "'" : "'s")
                                    + " conflicting copy, "
                                    + dateFormat.format(new Date())
                                    + ")" + FileUtil.getExtension(localLastVersion.getName(), true);

                            // DB entry			    
                            CloneFile localConflictVersion = (CloneFile) localLastVersion.clone();
                            localConflictVersion.setName(newFileName);
                            localConflictVersion.setUpdated(new Date());
                            localConflictVersion.setVersion(localLastVersion.getVersion() + 1);
                            localConflictVersion.setStatus(Status.RENAMED); // TODO this could be a problem for the remote client!
                            localConflictVersion.setMergedTo(lastRemoteVersion);
                            localConflictVersion.persist();

                            // File system
                            FileUtil.renameVia(localLastVersion.getFile(), localConflictVersion.getFile());

                            logger.info("Rename temp file to " + lastRemoteVersion.getFile() + " ...");
                            tempWinningFile.renameTo(lastRemoteVersion.getFile());

                            // Update DB
                            updateSyncStatus(localConflictVersion, SyncStatus.UPTODATE);
                        }
                    }
                }
            }
        }

        // Q empty!!
        tray.setStatusIcon(Tray.StatusIcon.UPTODATE);
        
        if (!newUpdatesMap.isEmpty()) {
            showNotification(newUpdatesMap);
        }

    }

    private void applyUpdate(CloneFile lastMatchingVersion, Update newFileUpdate) {
        // a) Merge-history
        if (newFileUpdate.getStatus() == Status.MERGED) {
            applyMergeChanges(newFileUpdate);
            return;
        }

        // b) Rename
        if (newFileUpdate.getStatus() == Status.RENAMED) {
            applyRenameOnlyChanges(lastMatchingVersion, newFileUpdate);
            return;
        }
 
        // c) Simply delete the last file
        if (newFileUpdate.getStatus() == Status.DELETED) {
            applyDeleteChanges(lastMatchingVersion, newFileUpdate);
            //logger.info("  - File "+newestVersion.getFileId()+", version "+newestVersion.getVersion()+": DELETE done: "+newestVersion.getRelativePath());
            return;
        }

        // d) Changed or new
        applyChangeOrNew(lastMatchingVersion, newFileUpdate);
        return;

        /*
        // Make DB entry
        CloneFile newVersion = db.createFile(profile, update);
        newUpdatesMap.get(newVersion.getFileId()).add(newVersion);
        
        // The last of this file id: perform changes!
        if (q.getMaxVersion(newVersion.getFileId()) == null) { // no file version left -> null!
        CloneFile localVersion = lastMatchingLocalVersionMap.get(newVersion.getFileId());
        List<CloneFile> newVersions = newUpdatesMap.get(newVersion.getFileId());
        
        try {
        applyChanges(localVersion, newVersions);
        }
        catch (CouldNotApplyUpdateException e) {
        logger.warning("Could not apply update "+update+". Re-adding to Queue ...");
        q.add(update);
        continue;
        }
        
        newVersion.setSyncStatus(CloneFile.SyncStatus.UPTODATE);
        addToDB(newVersion);
        
        fireLocalFileStatusChanged(newVersion);
        }
        
        // Not the last one: Just add to DB
        else {
        newVersion.setSyncStatus(CloneFile.SyncStatus.UPTODATE);
        addToDB(newVersion);
        }
        
         */

    }

    private void resolveConflict(CloneFile firstConflictingVersion, Update conflictUpdate) {
        ///// A. Adjust local history (first conflicting version - last local version)
        logger.info("resolveConflict: A. Adjusting local history of " + firstConflictingVersion + " ...");

        // I lose! Adjust complete history
        List<CloneFile> nextVersions = firstConflictingVersion.getNextVersions();

        File oldConflictingLocalFile = (nextVersions.isEmpty()) ? firstConflictingVersion.getFile() : nextVersions.get(nextVersions.size() - 1).getFile();
        CloneFile newConflictingLocalFile = null;

        List<CloneFile> versionsToAdjust = new ArrayList<CloneFile>();
        versionsToAdjust.add(firstConflictingVersion);
        versionsToAdjust.addAll(nextVersions);

        // Remove old DB entries, and add new ones
        em.getTransaction().begin();
        long version = 1;
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

        for (CloneFile cf : versionsToAdjust) {
            CloneFile cfclone = (CloneFile) cf.clone();

            // New filename
            String newFileName =
                    FileUtil.getBasename(cf.getName())
                    + " (" + config.getMachineName()
                    + (config.getMachineName().endsWith("s") ? "'" : "'s")
                    + " conflicting copy, "
                    + dateFormat.format(cfclone.getUpdated())
                    + ")" + FileUtil.getExtension(cf.getName(), true);

            // New file 
            cfclone.setStatus(Status.NEW);
            cfclone.setName(newFileName);
            cfclone.setVersion(version);

            newConflictingLocalFile = cfclone;

            logger.info("- Removing version " + cf + " from DB: " + cf.getAbsolutePath());
            em.remove(em.merge(cf));

            logger.info("- Adding adjusted version " + cfclone + " from DB: " + cfclone.getAbsolutePath());
            em.persist(cfclone);

            version++;
        }

        em.flush();
        em.getTransaction().commit();


        ///// B. Rename last local file to 'conflicting copy'	
        logger.info("resolveConflict: B. Renaming local file " + oldConflictingLocalFile + " to " + newConflictingLocalFile);
        FileUtil.renameVia(oldConflictingLocalFile, newConflictingLocalFile.getFile());

        // Q upload
        uploader.queue(newConflictingLocalFile);

        ///// C. Add updates to DB	
        logger.info("resolveConflict: C. Adding updates to DB: " + conflictUpdate);
        CloneFile winningVersion = addToDB(conflictUpdate);


        ///// D. Create 'winning' file
        logger.info("resolveConflict: D. Create/Download winning file ...");
        // TODO what if this is a rename-only history??? 

        File tempWinningFile = new File(winningVersion.getFile().getParentFile().getAbsoluteFile() + File.separator + ".ignore-assemble-to-" + winningVersion.getFile().getName());
        FileUtil.deleteRecursively(tempWinningFile); // just in case!

        try {
            // Download and assemble winning file
            downloadChunks(winningVersion);
            assembleFile(winningVersion, tempWinningFile);
        } catch (CouldNotApplyUpdateException ex) {
            logger.log(Level.SEVERE, "TODO TODO TODO TODO !!!!!!!! Unable to download/assemble winning file!", ex);
            // TODO TODO TODO error handling!
        }

        logger.info("resolveConflict: D2. Rename temp file to " + winningVersion.getFile() + " ...");
        tempWinningFile.renameTo(winningVersion.getFile());

        // Update DB
        updateSyncStatus(winningVersion, SyncStatus.UPTODATE);
    }

    private CloneFile addToDB(Update newFileUpdate) {
        return db.createFile(profile, newFileUpdate);
    }

    private CloneFile updateSyncStatus(CloneFile newFileVersion, SyncStatus syncStatus) {
        newFileVersion.setSyncStatus(syncStatus);
        newFileVersion.merge();

        return newFileVersion;
    }

    private void applyMergeChanges(Update newFileUpdate) {
        logger.info("- ChangeManager: Merge-history: adding updates to DB (that's it!) ...");
        
        CloneFile updateVersion = addToDB(newFileUpdate);        
        updateSyncStatus(updateVersion, SyncStatus.UPTODATE);
    }

    /**
     * 
     * <p>Note: files and folders are handled the same (in this case!). When
     * updating this method, make sure to check if it works for both!
     * 
     * @param lastMatchingVersion
     * @param newFileUpdates 
     */
    private void applyRenameOnlyChanges(CloneFile lastMatchingVersion, Update newFileUpdate) {
        if (!lastMatchingVersion.getFile().exists()) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error while renaming file {0}v{1}: {2} does NOT exist; Trying to download the file ...", new Object[]{lastMatchingVersion.getFileId(), lastMatchingVersion.getVersion(), lastMatchingVersion.getRelativePath()});
            }
            
            applyChangeOrNew(lastMatchingVersion, newFileUpdate);
            return;
        }

        ///// A. Add to DB
        CloneFile newestVersion = addToDB(newFileUpdate);

        ///// B. Rename current local version to the last update version
        logger.info("- ChangeManager: Renaming file " + lastMatchingVersion.getFile() + "  to  " + newestVersion.getFile());

        /// Do tests!
        if (!lastMatchingVersion.getFile().exists()) {
            logger.warning("- ChangeManager: Unable to rename file. " + lastMatchingVersion.getFile() + " does not exist");
            logger.warning("TODO TODO TODO what do we do in this case???");
            // TODO what do we do here?
            return;
        }

        if (newestVersion.getFile().exists()) {
            logger.warning("- ChangeManager: Unable to rename file. " + newestVersion.getFile() + " already exists.");
            logger.warning("TODO TODO TODO what do we do in this case???");
            // TODO what do we do here?
            return;
        }

        /// Do rename!
        File tempFile = new File(newestVersion.getAbsoluteParentDirectory() + "/.ignore-rename-to-" + newestVersion.getName());
        FileUtil.deleteRecursively(tempFile); // just in case!

        // No difference between folder and file !
        if (!lastMatchingVersion.getFile().renameTo(tempFile)) {
            logger.warning("ChangeManager Renaming NOT successful: from " + lastMatchingVersion.getFile() + " to " + tempFile);
            logger.warning("TODO TODO TODO what do we do in this case???");
            // TODO what do we do here?
            return;
        }

        tempFile.setLastModified(lastMatchingVersion.getLastModified().getTime());

        if (!tempFile.renameTo(newestVersion.getFile())) {
            logger.warning("ChangeManager Renaming NOT successful: from " + tempFile + " to " + newestVersion.getFile());
            logger.warning("TODO TODO TODO what do we do in this case???");
            // TODO what do we do here?	    
            return;
        }

        // Update DB
        updateSyncStatus(newestVersion, SyncStatus.UPTODATE);
    }

    /**
     * 
     * <p>Note: files and folders are handled the same (in this case!). When
     * updating this method, make sure to check if it works for both!
     * 
     * @param lastMatchingVersion
     * @param newFileUpdates 
     */
    private void applyDeleteChanges(CloneFile lastMatchingVersion, Update newFileUpdate) {
        logger.info("Deleting " + newFileUpdate.getName());

        ///// A. Add to DB
        CloneFile deletedVersion = addToDB(newFileUpdate);

        ///// B. Delete newest local file
        if (lastMatchingVersion == null) {
            return;
        }

        // No local version exists (weird!)
        if (!lastMatchingVersion.getFile().exists()) {
            logger.warning("Error while deleting file " + lastMatchingVersion.getFileId() + "v" + lastMatchingVersion.getVersion() + ": " + lastMatchingVersion.getRelativePath() + " does NOT exist.");
            return;
        }

        // No difference between folder and file !
        File tempDeleteFile = new File(lastMatchingVersion.getAbsoluteParentDirectory() + "/.ignore-delete-" + lastMatchingVersion.getName());
        FileUtil.deleteRecursively(tempDeleteFile);; // just in case!

        lastMatchingVersion.getFile().renameTo(tempDeleteFile);
        FileUtil.deleteRecursively(tempDeleteFile);

        // Update DB
        updateSyncStatus(deletedVersion, SyncStatus.UPTODATE);
    }

    /**
     * Steps:
     *  A. add new updates to DB
     * 
     *  if (isFolder):
     *      B. make folder to tempfile
     * 
     *  if (isFile):
     *      C. download chunks for the last update
     *      D. assemble chunks to tempfile
     * 
     *  if (local version exists):
     *      E. delete the local version
     * 
     *  F. move temp file to last update.
     * 
     * 
     * @param lastMatchingVersion
     * @param newFileUpdates
     * @throws CouldNotApplyUpdateException 
     */
    private void applyChangeOrNew(CloneFile lastMatchingVersion, Update newFileUpdate) {
        ///// A. Add to DB
        CloneFile newestVersion = addToDB(newFileUpdate);

        logger.info("- ChangeManager: Downloading/Updating " + newestVersion.getFile());

        // Skip conditions
        boolean unknownButFileExists = lastMatchingVersion == null && newestVersion.getFile().exists();

        if (unknownButFileExists) {
            logger.warning("File " + newestVersion.getFile() + " already exists. ");
            logger.warning("TODO TODO TODO what to do in this case?");

            // TODO what to do here?
            return;
        }

        // Temp files
        File tempNewFile = new File(newestVersion.getAbsoluteParentDirectory() + "/.ignore-assemble-to-" + newestVersion.getName());
        File tempDeleteFile = new File(newestVersion.getAbsoluteParentDirectory() + "/.ignore-delete-" + newestVersion.getName());

        FileUtil.deleteRecursively(tempNewFile); // just in case!
        FileUtil.deleteRecursively(tempDeleteFile); // just in case!


        ///// B. Make folder
        if (newestVersion.isFolder()) {
            tempNewFile.mkdirs();
        }
        
        ///// C+D. Download and assemble file
        else {
            try {
                downloadChunks(newestVersion);
                assembleFile(newestVersion, tempNewFile);
            } catch (CouldNotApplyUpdateException e) {
                logger.log(Level.SEVERE, "TODO TODO TODO Warning: could not download/assemble " + newestVersion, e);
                // TODO TODO error handling!!
                return;
            }
        }

        ///// E. delete local version (if there is one)
        if (lastMatchingVersion != null && lastMatchingVersion.getFile().exists()) {
            lastMatchingVersion.getFile().renameTo(tempDeleteFile);
            FileUtil.deleteRecursively(tempDeleteFile);
        }

        ///// F. Move temp file tonew file
        tempNewFile.setLastModified(newestVersion.getLastModified().getTime());
        tempNewFile.renameTo(newestVersion.getFile());
        FileUtil.deleteRecursively(tempNewFile);


        // Update DB
        updateSyncStatus(newestVersion, SyncStatus.UPTODATE);
    }

    private void downloadChunks(CloneFile file) throws CouldNotApplyUpdateException {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Downloading file " + file.getRelativePath() + " ...");
        }

        for (CloneChunk chunk : file.getChunks()) {
            File chunkCacheFile = config.getCache().getCacheChunk(chunk);

            if (/*chunk.getCacheStatus() == CacheStatus.CACHED &&*/chunkCacheFile.exists()) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("- Chunk " + chunk + " found in local cache.");
                }
                continue;
            }

            try {
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("- Downloading chunk " + chunk + " ...");
                }

                //fireChunkDownloadAttempt(chunk, 1);
                transfer.download(new RemoteFile(chunk.getFileName()), chunkCacheFile);

                // Change DB state of chunk
                //chunk.setCacheStatus(CacheStatus.CACHED);
                /*
                em.getTransaction().begin();
                em.merge(chunk);
                em.flush();
                em.getTransaction().commit();*/
            } catch (StorageException e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "- ERR: Chunk " + chunk + " not found (or something else)", e);
                }

                throw new CouldNotApplyUpdateException(e);
                // fireChunkDownloaded(chunk, false);
                // fireFileDownloaded(file, false);
            }

            // Yey!
            //fireChunkDownloaded(chunk, true);
        }

        // Yeyyy!
        if (logger.isLoggable(Level.INFO)) {
            logger.info("- File " + file.getRelativePath() + " downloaded; Assembling ...");
        }


    }

    private void assembleFile(CloneFile cf, File tempFile) throws CouldNotApplyUpdateException {
        try {
            FileOutputStream fos = new FileOutputStream(tempFile, false);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("- Decrypting chunks to temp file  " + tempFile.getAbsolutePath() + " ...");
            }

            for (CloneChunk chunk : cf.getChunks()) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("- Chunk " + config.getCache().getCacheChunk(chunk) + " ...");
                }

                // Read file to buffer
                File chunkFile = config.getCache().getCacheChunk(chunk);

                byte[] packed = FileUtil.readFile(chunkFile);
                byte[] unpacked = FileUtil.unpack(packed, cf.getProfile().getRepository().getEncryption());

                // Write decrypted chunk to file
                fos.write(unpacked);
            }

            fos.close();

            // Rename to real file
            //if (logger.isLoggable(Level.INFO)) logger.info("["+account.getName()+"] - Rename to final file "+cf.getAbsolutePath()+" ...");
        } catch (Exception e) {
            throw new CouldNotApplyUpdateException(e);
        }

        if (logger.isLoggable(Level.INFO)) {
            logger.info("- File " + cf.getRelativePath() + " downloaded");
        }
        //fireFileDownloaded(file, true);
    }

    /**
     * Returns true if the local client loses the conflict.
     */
    private boolean isLocalConflict(CloneFile existingVersion, Update update) {
        // Test different positive cases.
        // Please note, that the order of the IF-tests is important!
        
        if (existingVersion == null) {
            return false;
        }
        
        if (existingVersion.getStatus() == Status.DELETED && update.getStatus() == Status.DELETED) {
            return false;
        }
        
        if (existingVersion.getStatus() == Status.MERGED && update.getStatus() == Status.MERGED
                && existingVersion.getMergedTo() != null 
                && existingVersion.getMergedTo().getFileId().equals(update.getMergedFileId())) {
            
            return false;            
        }
        
        if (existingVersion.getStatus() == Status.RENAMED && update.getStatus() == Status.RENAMED
                && existingVersion.getPath().equals(update.getPath()) 
                && existingVersion.getName().equals(update.getName())) {
            
            return false;
        }
        
        if (existingVersion.getStatus() == Status.NEW && update.getStatus() == Status.NEW
                && existingVersion.getFileSize() == update.getFileSize()
                && existingVersion.getChecksum() == update.getChecksum()
                && existingVersion.getPath().equals(update.getPath()) 
                && existingVersion.getName().equals(update.getName())) {
            
            return false;
        }        
        
        if (existingVersion.getStatus() == Status.CHANGED && update.getStatus() == Status.CHANGED
                && existingVersion.getFileSize() == update.getFileSize()
                && existingVersion.getChecksum() == update.getChecksum()
                && existingVersion.getPath().equals(update.getPath()) 
                && existingVersion.getName().equals(update.getName())) {
            
            return false;
        }        
      
        
        // Okay, from this point on, we DO have a conflict.
        // Now we have to decide whether to care about it or not.

        // If we were first, the remote client has to fix it!
        if (existingVersion.getUpdated().before(update.getUpdated())) {
            logger.info("- Nothing to resolve. I win. Local version " + existingVersion + " is older than update " + update);
            return false;
        }

        // Rare case: Updated at the same time; Choose client with the "smallest" name (alphabetical)
        if (existingVersion.getUpdated().equals(update.getUpdated()) && config.getMachineName().compareTo(update.getClientName()) == 1) {
            logger.info("- Nothing to resolve. I win. RARE CASE: Decision by client name!");
            return false;
        }

        // Conflict, I lose!
        return true;
    }

    public void showNotification(Map<Long, List<Update>> appliedUpdates) {
        tray.setStatusIcon(Tray.StatusIcon.UPTODATE);

        // Skip notification
        if (appliedUpdates.isEmpty()) {
            return;
        }

        // Poke updated files
        for (List<Update> updates : appliedUpdates.values()) {
            Update lastUpdate = updates.get(updates.size() - 1);

            CloneFile file = db.getFileOrFolder(profile, lastUpdate.getFileId(), lastUpdate.getVersion());

            if (file != null) {
                desktop.touch(file.getFile());
            }
        }

        // Firgure out if only one client edited stuff
        String clientName = null;

        a:
        for (List<Update> updates : appliedUpdates.values()) {
            b:
            for (Update u : updates) {
                if (clientName == null) {
                    clientName = u.getClientName();
                } else if (!clientName.equals(u.getClientName())) {
                    clientName = null;
                    break a;
                }
            }
        }

        // Only one client
        if (clientName != null) {
            CloneClient client = db.getClient(profile, clientName, true);

            File imageFile = client.getUserImageFile();
            String summary = (client.getUserName() != null) ? client.getUserName() : client.getMachineName();
            String body;

            if (!imageFile.exists()) {
                imageFile = new File(config.getResDir() + File.separator + "logo48.png");
            }

            Long[] fileIds = appliedUpdates.keySet().toArray(new Long[0]);

            // Only one file
            if (fileIds.length == 1) {
                List<Update> updates = appliedUpdates.get(fileIds[0]);

                Update lastUpdate = updates.get(updates.size() - 1);
                Update secondLastUpdate = (updates.size() > 1) ? updates.get(updates.size() - 2) : null;
                // TODO this should be CloneFile instances

                switch (lastUpdate.getStatus()) {
                    case RENAMED:
                        if (secondLastUpdate != null) {
                            body = "renamed '" + secondLastUpdate.getName() + "' to '" + lastUpdate.getName() + "'";
                            break;
                        } else {
                            body = "renamed '" + lastUpdate.getName() + "'";
                            break;
                        }

                    case DELETED:
                        body = "deleted '" + lastUpdate.getName() + "'";
                        break;
                    case CHANGED:
                        body = "edited '" + lastUpdate.getName() + "'";
                        break;
                    case NEW:
                        body = "added '" + lastUpdate.getName() + "'";
                        break;
                    case MERGED:
                        return; // TODO this shouldn't land here!
                    default:
                        body = "updated '" + lastUpdate.getName() + "'";
                }

                tray.notify(summary, body, imageFile);
            } // More files
            else {
                tray.notify(summary, "updated " + appliedUpdates.size() + " file(s)", imageFile);
            }

        } // More than one client
        else {
            File imageFile = new File(config.getResDir() + File.separator + "logo48.png");
            tray.notify(Constants.APPLICATION_NAME, appliedUpdates.size() + " file(s) updated", imageFile);
        }
    }
}
