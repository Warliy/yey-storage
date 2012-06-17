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
package org.syncany.index.requests;

import java.util.logging.Level;
import org.syncany.config.Folder;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import org.syncany.Constants;
import org.syncany.db.CloneFile;
import org.syncany.db.CloneFile.Status;
import org.syncany.gui.desktop.Desktop;

/**
 *
 * @author Philipp C. Heckel
 */
public class DeleteIndexRequest extends SingleRootIndexRequest {
    private static final Logger logger = Logger.getLogger(DeleteIndexRequest.class.getSimpleName());    
    private static final Desktop desktop = Desktop.getInstance();
    
    private CloneFile dbFile;
    private File file;

    public DeleteIndexRequest(Folder root, File file) {
        super(root);
        
        this.dbFile = null;
        this.file = file;
    }
    
    public DeleteIndexRequest(Folder root, CloneFile dbFile) {
        super(root);
        
        this.dbFile = dbFile;
        this.file = dbFile.getFile();
    }    

    public File getFile() {
        return file;
    }

    @Override
    public void process() {
        // .ignore file
        if (file.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            return;
        }
        
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Indexer: Deleting file  {0}", file.getAbsolutePath());
        }

        // Find last version of this file in DB
        if (dbFile == null) {
            dbFile = db.getFileOrFolder(root, file);
        }

        // Not found?!
        if (dbFile == null) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Indexer: File not found in DB ({0}). IGNORING.", file.getAbsolutePath());
            }
            
            return;
        }

        // File found in DB.
        CloneFile deletedVersion = (CloneFile) dbFile.clone();

        // Updated changes
        deletedVersion.setVersion(deletedVersion.getVersion()+1);
        deletedVersion.setUpdated(new Date());
        deletedVersion.setStatus(Status.DELETED);
        deletedVersion.setSyncStatus(CloneFile.SyncStatus.LOCAL);
        deletedVersion.setClientName(config.getMachineName());

        deletedVersion.merge();
        
        // Notify file manager (invalidate!)
        desktop.touch(file);        

        // Delete children (if directory) -- RECURSIVELY !!
        if (dbFile.isFolder()) {            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Indexer: Deleting CHILDREN of {0} ...", file);
            }
            
            List<CloneFile> children = db.getChildren(dbFile);

            for (CloneFile child : children) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Indexer: Delete CHILD {0} ...", child.getAbsolutePath());
                }
                
                // Do it!
                new DeleteIndexRequest(root, child).process();
            }
        }

    }
}