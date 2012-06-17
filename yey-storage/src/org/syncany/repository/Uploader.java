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
package org.syncany.repository;

import java.util.Date;
import org.syncany.repository.files.RemoteFile;
import org.syncany.exceptions.StorageException;
import org.syncany.db.CloneChunk;
import org.syncany.db.CloneFile;
import org.syncany.config.Config;
import org.syncany.config.Profile;
import org.syncany.connection.plugins.TransferManager;
import org.syncany.db.CloneFile.SyncStatus;
import org.syncany.db.DatabaseHelper;
import org.syncany.gui.desktop.Desktop;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.gui.tray.Tray;

/**
 * Represents the remote storage.
 * Processes upload and download requests asynchonously.
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Uploader {
    private static final int CACHE_FILE_LIST = 60000;
    
    private static final Config config = Config.getInstance();
    private static final Logger logger = Logger.getLogger(Uploader.class.getSimpleName());
    private static final Tray tray = Tray.getInstance();
    private static final Desktop desktop = Desktop.getInstance();    
    private static final DatabaseHelper db = DatabaseHelper.getInstance();
    
    private Profile profile;
    private TransferManager transfer;
    private BlockingQueue<CloneFile> queue;
    private Thread worker;

    private Map<String, RemoteFile> fileList;
    private Date cacheLastUpdate;
    
    public Uploader(Profile profile) {
        this.profile = profile;
        this.queue = new LinkedBlockingQueue<CloneFile>();

        this.worker = null; // cmp. method 'start'
    }

    public synchronized void start() {
        if (worker != null)
            return;

        transfer = profile.getRepository().getConnection().createTransferManager();
        
        worker = new Thread(new Worker(), "UploaderWorker");        
        worker.start();
    }

    public synchronized void stop() {
        if (worker == null || worker.isInterrupted())
            return;

        worker.interrupt();
        worker = null;
    }

    public void queue(CloneFile file) {
        try {
            queue.put(file);
        }
        catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            try {
                CloneFile file;

                while (null != (file = queue.take())) {
                    processRequest(file);
                }
            }
            catch (InterruptedException iex) {
                iex.printStackTrace();
            }
        }

        private void processRequest(CloneFile file) {
            if (tray.getStatusIcon() != Tray.StatusIcon.UPDATING) {
                tray.setStatusIcon(Tray.StatusIcon.UPDATING);
            }

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "UploadManager: Uploading file {0} ...", file.getFileName());
            }

            // Update DB sync status
            if (!file.isFolder()) {
                file.setSyncStatus(CloneFile.SyncStatus.SYNCING);
                file.merge();

                touch(file, SyncStatus.SYNCING);
            }

            // Get file list (to check if chunks already exist)
            if (cacheLastUpdate == null || fileList == null 
                    || System.currentTimeMillis()-cacheLastUpdate.getTime() > CACHE_FILE_LIST) {
                
                try { 
                    fileList = transfer.list();
                }
                catch (StorageException ex) {
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }
            }
            
            for (CloneChunk chunk : file.getChunks()) {
                // Chunk has been uploaded before
                if (fileList.containsKey(chunk.getFileName())) {
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "UploadManager: Chunk {0} already uploaded", chunk.getFileName());
                    }
                    
                    continue;
                }

                // Upload it!
                try {
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "UploadManager: Uploading chunk {0} ...", chunk.getFileName());
                    }
                    
                    transfer.upload(config.getCache().getCacheChunk(chunk), new RemoteFile(chunk.getFileName()));
                } 
                catch (StorageException ex) {
                    logger.log(Level.SEVERE,"UploadManager: Uploading chunk "+chunk.getFileName()+" FAILED !!",ex);
                    return;
                }

            }

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "UploadManager: File {0} uploaded", file.getAbsolutePath());
            }

            // Update DB sync status
            file.setSyncStatus(SyncStatus.UPTODATE);
            file.merge();
                        
            touch(file, SyncStatus.UPTODATE);

            if (queue.isEmpty()) {
                tray.setStatusIcon(Tray.StatusIcon.UPTODATE);
            }
        }

        private void touch(CloneFile file, SyncStatus syncStatus) {
            // Touch myself
            desktop.touch(file.getFile());
            
            // Touch parents
            CloneFile childCF = file;
            CloneFile parentCF;

            while (null != (parentCF = childCF.getParent())) {
                if (parentCF.getSyncStatus() != syncStatus) {
                    parentCF.setSyncStatus(syncStatus);
                    parentCF.merge();
                    
                    desktop.touch(parentCF.getFile());
                }

                childCF = parentCF;
            }
        }

        private void updateParents(CloneFile file) {
        // Update parent sync status
            CloneFile childCF = file;
            CloneFile parentCF;

            while (null != (parentCF = childCF.getParent())) {
            SyncStatus parentSyncStatus = parentCF.getSyncStatus();

            int uptodateCount = 0;
            int conflictCount = 0;

            List<CloneFile> allChildren = db.getAllChildren(parentCF);

            c: for (CloneFile child : allChildren) {
                if (child.getSyncStatus() == SyncStatus.SYNCING) {

                parentSyncStatus = SyncStatus.SYNCING;
                break c;
                }

                if (child.getSyncStatus() == SyncStatus.UPTODATE)
                uptodateCount++;

                if (child.getSyncStatus() == CloneFile.SyncStatus.CONFLICT)
                conflictCount++;
            }

            if (uptodateCount == allChildren.size())
                parentSyncStatus = CloneFile.SyncStatus.UPTODATE;

            else if (conflictCount > 0)
                parentSyncStatus = CloneFile.SyncStatus.CONFLICT;

            if (parentSyncStatus != parentCF.getSyncStatus()) {
                logger.info("UploadManager: UPDATE ICON FOR "+parentCF.getFile()+" TO: "+parentSyncStatus);

                config.getDatabase().getEntityManager().getTransaction().begin();
                parentCF.setSyncStatus(parentSyncStatus);
                config.getDatabase().getEntityManager().merge(parentCF);
                config.getDatabase().getEntityManager().flush();
                config.getDatabase().getEntityManager().getTransaction().commit();

                desktop.touch(parentCF.getFile());
            }

            childCF = parentCF;
            }

        }
    }
}
