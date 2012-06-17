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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import name.pachler.nio.file.contrib.BufferedWatcher;
import name.pachler.nio.file.contrib.ExtendedWatchEvent;
import name.pachler.nio.file.contrib.RenamePathContext;
import name.pachler.nio.file.contrib.RenameWatchEventKind;
import name.pachler.nio.file.StandardWatchEventKind;
import name.pachler.nio.file.WatchEvent;
import name.pachler.nio.file.WatchKey;
import name.pachler.nio.file.contrib.WatchListener;
import name.pachler.nio.file.ext.ExtendedWatchEventKind;
import name.pachler.nio.file.impl.PathWatchEvent;
import org.syncany.config.Folder;
import org.syncany.config.Profile;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class CommonLocalWatcher extends LocalWatcher implements WatchListener {
    private BufferedWatcher watcher;
    private Map<WatchKey, Folder> keyRootMap;

    public CommonLocalWatcher() {
        this.watcher = new BufferedWatcher();
        this.keyRootMap = new HashMap<WatchKey, Folder>();
    }        

    @Override
    public void start() {
        watcher.start();
    }

    @Override
    public void stop() {
        watcher.stop();
    }

     @Override
    public synchronized void watch(Profile profile) {
        for (Folder folder : profile.getFolders().list()) {
            if (!folder.isActive() || folder.getLocalFile() == null) {
                continue;
            }
            
            try {
                WatchKey rootKey = watcher.addWatch(folder.getLocalFile(), true, this);
                keyRootMap.put(rootKey, folder);
            }
            catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to add log to profile folder "+folder.getLocalFile(), ex);
            }
        }
    }

    @Override
    public void unwatch(Profile profile) {
        for (Folder folder : profile.getFolders().list()) {
            if (!folder.isActive() || folder.getLocalFile() == null) {
                continue;
            }
            
            watcher.removeWatch(folder.getLocalFile());
            
            // Remove form map
            allfolders: for (Map.Entry<WatchKey, Folder> e : keyRootMap.entrySet()) {
                if (e.getValue().equals(folder)) {
                    keyRootMap.remove(e.getKey());
                    break allfolders;
                }
            }
        }
    }

    @Override
    public void watchEventOccurred(WatchKey parentKey, WatchEvent event) {
        if (event instanceof PathWatchEvent) {
            File file = watcher.getEventFile(event, parentKey);

            WatchKey rootKey = watcher.getRootKey(parentKey);
            Folder root = keyRootMap.get(rootKey);

            // CREATE / CHANGE
            if (event.kind() == StandardWatchEventKind.ENTRY_CREATE
                || event.kind() == ExtendedWatchEventKind.ENTRY_RENAME_TO
                || event.kind() == StandardWatchEventKind.ENTRY_MODIFY) {

                queueCheckFile(root, file);
            }

            // DELETE
            else if (event.kind() == StandardWatchEventKind.ENTRY_DELETE
                || event.kind() == ExtendedWatchEventKind.ENTRY_RENAME_FROM) {

                queueDeleteFile(root, file);
            }
        }
        
        // MOVE
        else if (event.kind() == RenameWatchEventKind.ENTRY_RENAME_FROM_TO) {
            RenamePathContext renameContext = (RenamePathContext) event.context();
            ExtendedWatchEvent fromEvent = renameContext.getFromEvent();
            ExtendedWatchEvent toEvent = renameContext.getToEvent();            
            
            File fromFile = watcher.getEventFile(fromEvent.getEvent(), fromEvent.getParentKey());
            File toFile = watcher.getEventFile(toEvent.getEvent(), toEvent.getParentKey());
            WatchKey fromRootKey = watcher.getRootKey(fromEvent.getParentKey());
            WatchKey toRootKey = watcher.getRootKey(toEvent.getParentKey());
            
            if (fromRootKey == null || toRootKey == null) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Unable to get root key for FROM or TO event: from = {0}, to = {1}. IGNORING EVENT.", new Object[]{fromRootKey, toRootKey});
                }
                
                return;
            }
            
            Folder fromRoot = keyRootMap.get(fromRootKey);
            Folder toRoot = keyRootMap.get(toRootKey);

            queueMoveFile(fromRoot, fromFile, toRoot, toFile);
        }                 
        
        else if (event.kind() == StandardWatchEventKind.OVERFLOW
            || event.kind() == ExtendedWatchEventKind.KEY_INVALID) {
            
            // Ignore.
        }              
        
        else {
            logger.log(Level.INFO, "Unhandled event: {0}", event);
        }        
    }  

}
