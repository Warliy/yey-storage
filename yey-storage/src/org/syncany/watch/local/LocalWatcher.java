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
package org.syncany.watch.local;

import org.syncany.config.Config;
import org.syncany.Environment;
import org.syncany.Environment.OperatingSystem;
import org.syncany.config.Folder;
import org.syncany.config.Profile;
import org.syncany.index.Indexer;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.Constants;

/**
 *
 * @author oubou68, pheckel
 */
public abstract class LocalWatcher {
    protected static final Logger logger = Logger.getLogger(LocalWatcher.class.getSimpleName());
    protected static final Environment env = Environment.getInstance();
    protected static LocalWatcher instance;
    protected Config config;
    protected Indexer indexer;

    public LocalWatcher() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Creating watcher ...");
        }

        initDependencies();
    }

    private void initDependencies() {
        config = Config.getInstance();
        indexer = Indexer.getInstance();
    }

    public void queueCheckFile(Folder root, File file) {
        // Exclude ".ignore*" files from everything
        if (file.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            //logger.info("Watcher: Ignoring file "+file.getAbsolutePath());
            return;
        }
        
        // File vanished!
        if (!file.exists()) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Watcher: File {0} vanished. IGNORING.", file);
            }
            
            return;
        }

        // Add to queue
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Watcher: Checking new/modified file {0}", file);
        }

        indexer.queue(root, file);            
    }

    public void queueMoveFile(Folder fromRoot, File fromFile, Folder toRoot, File toFile) {
        // Exclude ".ignore*" files from everything
        if (fromFile.getName().startsWith(Constants.FILE_IGNORE_PREFIX)
                || toFile.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            
            //logger.info("Watcher: Ignoring file "+fromFile.getAbsolutePath());
            return;
        }
        
        // File vanished!
        if (!toFile.exists()) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Watcher: File {0} vanished. IGNORING.", toFile);
            }
            
            return;
        }

        // Add to queue
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Watcher: Moving file {0} TO {1}", new Object[]{fromFile, toFile});
        }
        
        indexer.queueMoved(fromRoot, fromFile, toRoot, toFile);
    }

    public void queueDeleteFile(Folder root, File file) {
        // Exclude ".ignore*" files from everything
        if (file.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            //logger.info("Watcher: Ignoring file "+file.getAbsolutePath());
            return;
        }

        // Add to queue
        if (logger.isLoggable(Level.INFO)) {        
            logger.log(Level.INFO, "Watcher: Deleted file {0}", file);
        }
        
        indexer.queueDeleted(root, file);
    }

    public static synchronized LocalWatcher getInstance() {
        if (instance != null) {
            return instance;
        }

        if (env.getOperatingSystem() == OperatingSystem.Linux
            || env.getOperatingSystem() == OperatingSystem.Windows) {
            
            instance = new CommonLocalWatcher(); 
            return instance;
        }


        throw new RuntimeException("Your operating system is currently not supported: " + System.getProperty("os.name"));
    }

    public abstract void start();

    public abstract void stop();

    public abstract void watch(Profile profile);

    public abstract void unwatch(Profile profile);
}
