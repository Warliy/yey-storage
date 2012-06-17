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
package org.syncany.index;

import java.util.List;
import org.syncany.config.Folder;
import org.syncany.config.Profile;
import org.syncany.db.CloneFile;
import org.syncany.index.requests.CheckIndexRequest;
import org.syncany.index.requests.DeleteIndexRequest;
import org.syncany.index.requests.IndexRequest;
import org.syncany.index.requests.MoveIndexRequest;
import org.syncany.watch.local.LocalWatcher;
import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.Application;
import org.syncany.db.DatabaseHelper;
import org.syncany.util.FileLister;

/**
 * Indexes new and changed files and adds corresponding database entries
 * if necessary. The indexer is mainly called by the {@link Watcher} inside the
 * {@link Application} object.
 *
 * <p>It mainly consists of a request queue and one worker thread that handles
 * events such as new, changed, renamed or deleted files or folders.
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Indexer {
    private static final Logger logger = Logger.getLogger(Indexer.class.getSimpleName());
    private static Indexer instance;
    
    private DatabaseHelper db;
    private BlockingQueue<IndexRequest> queue;
    private Thread worker;

    public Indexer() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Creating indexer ...");
        }
        
        this.db = DatabaseHelper.getInstance();
        this.queue = new LinkedBlockingQueue<IndexRequest>();
        this.worker = null; // cp. start()
    }
    
    public static synchronized Indexer getInstance() {
        if (instance == null) {
            instance = new Indexer();
        }
        
        return instance;
    }

    public synchronized void start() {
        // Already running!
        if (worker != null) {
            return;
        }
        
        // Start it
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Starting indexer thread ...");
        }
        
        worker = new Thread(new IndexWorker(), "Indexer");
        worker.start();
    }

    public synchronized void stop() {
        if (worker == null) {
            return;
        }
        
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Stopping indexer thread ...");
        }
        
        worker.interrupt();
        worker = null;
    }

    public void index(Profile profile) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Reading folders in profile ''{0}'' ...", profile.getName());
        }
                
        for (Folder folder : profile.getFolders().list()) {
            if (!folder.isActive() || folder.getLocalFile() == null) {
                continue;
            }
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "- Folder {0} ...", folder.getLocalFile());
            }            
            
            // Check for files that do NOT exist anymore
            List<CloneFile> dbFiles = db.getFiles(folder);
            
            for (CloneFile dbFile : dbFiles) {
                if (!dbFile.getFile().exists()) {
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "  File {0} does NOT exist anymore. Marking as deleted.", dbFile.getFile());
                    }          
                    
                    new DeleteIndexRequest(folder, dbFile).process();
                }
            }
                    
            // Check existing files
            new FileLister(folder.getLocalFile(), new FileListerListenerImpl(folder, this, true)).start();
        }	
        
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Startup indexing of profile {0} finished.", profile);
        }          
    }
    
    public void index(Folder root, File file) {
        new CheckIndexRequest(root, file).process();
    }
    
    /**
     * Check database to find matches for the given file. If no matches
     * or previous versions are found, the file is re-indexed completely.
     * 
     * @param file
     */
    public void queue(Folder root, File file) {
        queue.add(new CheckIndexRequest(root, file));
    }

    /**
     * Adjusts the entry of a file that has been moved.
     * @param fromFile
     * @param toFile
     */
    public void queueMoved(Folder fromRoot, File fromFile, Folder toRoot, File toFile) {
        queue.add(new MoveIndexRequest(fromRoot, fromFile, toRoot, toFile));
    }

    public void queueDeleted(Folder root, File file) {
        queue.add(new DeleteIndexRequest(root, file));
    }

    private class IndexWorker implements Runnable {
        @Override
        public void run() {
            try {
                IndexRequest req;
                
                while (null != (req = queue.take())) {
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "Processing request {0}", req);
                    }
                    
                    req.process();
                }
            }
            catch (InterruptedException ex) {
                logger.log(Level.WARNING, "Indexer interrupted. EXITING.");
                return;
            }
        }
    }
}
