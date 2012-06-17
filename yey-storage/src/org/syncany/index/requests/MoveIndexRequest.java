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
import org.syncany.util.FileUtil;

/**
 *
 * @author Philipp C. Heckel
 */
public class MoveIndexRequest extends IndexRequest {
    private static final Logger logger = Logger.getLogger(MoveIndexRequest.class.getSimpleName());
    private static final Desktop desktop = Desktop.getInstance();
    
    private CloneFile dbFromFile;
    
    private Folder fromRoot;
    private File fromFile;
    
    private Folder toRoot;
    private File toFile;

    public MoveIndexRequest(Folder fromRoot, File fromFile, Folder toRoot, File toFile) {
        super();

        this.fromRoot = fromRoot;
        this.fromFile = fromFile;
        
        this.toRoot = toRoot;
        this.toFile = toFile;
    }

    public MoveIndexRequest(CloneFile dbFromFile, Folder toRoot, File toFile) {
        this(dbFromFile.getRoot(), dbFromFile.getFile(), toRoot, toFile);

        this.dbFromFile = dbFromFile;
    }

    /*
     * TODO moving a file from one ROOT to another does not work (cp. database!)
     * 
     */
    @Override
    public void process() {
        // .ignore file
        if (fromFile.getName().startsWith(Constants.FILE_IGNORE_PREFIX) 
                || toFile.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            
            return;
        }
        
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Indexer: Updating moved file {0} TO {1}", new Object[]{fromFile.getAbsolutePath(), toFile.getAbsolutePath()});
        }
        
        // Look for file in DB
        if (dbFromFile == null) {
            dbFromFile = db.getFileOrFolder(fromRoot, fromFile);
            
            // No file found in DB.
            if (dbFromFile == null) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Indexer: Source file not found in DB ({0}). Indexing {1} as new file.", new Object[]{fromFile.getAbsolutePath(), toFile.getAbsolutePath()});
                }

                new CheckIndexRequest(toRoot, toFile).process();
                return;
            }            
        }

        // Parent 
        String relToParentFolder = FileUtil.getRelativeParentDirectory(toRoot.getLocalFile(), toFile);
        String absToParentFolder = FileUtil.getAbsoluteParentDirectory(toFile);
        CloneFile cToParentFolder = db.getFolder(toRoot, new File(absToParentFolder));

        // File found in DB.
        CloneFile dbToFile = (CloneFile) dbFromFile.clone();

        // Updated changes
        dbToFile.setRoot(toRoot);
        dbToFile.setLastModified(new Date(toFile.lastModified()));
        dbToFile.setPath(relToParentFolder);
        dbToFile.setName(toFile.getName());
        dbToFile.setFileSize((toFile.isDirectory()) ? 0 : toFile.length());
        dbToFile.setVersion(dbToFile.getVersion()+1);
        dbToFile.setUpdated(new Date());
        dbToFile.setStatus(Status.RENAMED);
        dbToFile.setSyncStatus(CloneFile.SyncStatus.UPTODATE);
        dbToFile.setClientName(config.getMachineName());

        dbToFile.setParent(cToParentFolder);
        dbToFile.merge();
	    
        // Notify file manager
        desktop.touch(dbToFile.getFile());
	    
        // Update children (if directory) -- RECURSIVELY !!
        if (dbToFile.isFolder()) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Indexer: Updating CHILDREN of {0} ...", toFile);
            }
            
            List<CloneFile> children = db.getChildren(dbFromFile);

            for (CloneFile child : children) {
                File childFromFile = child.getFile();
                File childToFile = new File(absToParentFolder+File.separator+toFile.getName()+File.separator+child.getName());

                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Indexer: Updating children of moved file {0} TO {1}", new Object[]{childFromFile.getAbsolutePath(), childToFile.getAbsolutePath()});
                }
                
                // Do it!
                new MoveIndexRequest(fromRoot, childFromFile, toRoot, childToFile).process();
            }
        }
    }
}