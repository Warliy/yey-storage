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

import java.util.Enumeration;
import org.syncany.config.Folder;
import org.syncany.db.CloneFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.Constants;
import org.syncany.db.CloneChunk;
import org.syncany.db.CloneFile.Status;
import org.syncany.index.Chunker;
import org.syncany.index.Chunker.FileChunk;
import org.syncany.util.FileUtil;

/**
 *
 * @author Philipp C. Heckel
 */
public class NewIndexRequest extends SingleRootIndexRequest {
    private static final Logger logger = Logger.getLogger(NewIndexRequest.class.getSimpleName());
    
    private File file;
    private CloneFile previousVersion;
    private Chunker chunker;

    public NewIndexRequest(Folder root, File file, CloneFile previousVersion) {
        super(root);
        
        this.file = file;        
        this.previousVersion = previousVersion;        
        this.chunker = new Chunker();
    }

    @Override
    public void process() {
        // .ignore file
        if (file.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            return;
        }
        
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Indexer: Indexing new file {0} ...", file);
        }
                
        // File vanished
        if (!file.exists()) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Indexer: Error indexing file {0}: File does NOT exist. Ignoring.", file);
            }
            
            return;
        }
        
        // Create DB entry
        CloneFile newVersion = (previousVersion == null) ? addNewVersion() : addChangedVersion();                      
        
        File parentFile = FileUtil.getCanonicalFile(file.getParentFile());
        newVersion.setParent(db.getFolder(root, parentFile));
        
        // Process folder and files differently
        if (file.isDirectory()) {
            processFolder(newVersion);
        }
        
        else if (file.isFile()) {
            processFile(newVersion);
        }
    }

    private CloneFile addNewVersion() {
        CloneFile newVersion = new CloneFile(root, file);        
        
        newVersion.setVersion(1);
        newVersion.setSyncStatus(CloneFile.SyncStatus.LOCAL);
        newVersion.setStatus(Status.NEW);
        newVersion.setUpdated(new Date());
        newVersion.setClientName(config.getMachineName());
        
        return newVersion;
    }
    
    private CloneFile addChangedVersion() {        
        CloneFile newVersion = (CloneFile) previousVersion.clone();

        newVersion.setVersion(previousVersion.getVersion()+1);
        newVersion.setUpdated(new Date());
        newVersion.setStatus(Status.CHANGED);
        newVersion.setSyncStatus(CloneFile.SyncStatus.LOCAL);
        newVersion.setClientName(config.getMachineName());
        newVersion.setChunks(new ArrayList<CloneChunk>()); // otherwise we would append!
        newVersion.setFileSize(file.length());
        newVersion.setLastModified(new Date(file.lastModified()));      
        
        return newVersion;
    }
    
    private void processFolder(CloneFile cf) {        
        // Add rest of the DB stuff 
        cf.setChecksum(0);
        cf.persist();
        
        // Analyze file tree (if directory) -- RECURSIVELY!!
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Indexer: Indexing CHILDREN OF {0} ...", file);
        }

        for (File child : file.listFiles()) {
            // Ignore .ignore files
            if (child.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {                
                continue; 
            }

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Indexer: Parent: {0} / CHILD {1} ...", new Object[]{file, child});
            }
            
            // Do it!
            new NewIndexRequest(root, child, null).process();
        }
        
    }

    private void processFile(CloneFile cf) {
        try {
            // 1. Chunk it!
            FileChunk chunkInfo = null;
            Enumeration<FileChunk> chunks = chunker.createChunks(file, root.getProfile().getRepository().getChunkSize());

            while (chunks.hasMoreElements()) {
                chunkInfo = chunks.nextElement();

                // create chunk in DB (or retrieve it)
                CloneChunk chunk = db.getChunk(chunkInfo.getChecksum(), true);
                cf.addChunk(chunk); 

                // write encrypted chunk (if it does not exist)
                File chunkCacheFile = config.getCache().getCacheChunk(chunk);

                if (!chunkCacheFile.exists()) {
                    byte[] packed = FileUtil.pack(chunkInfo.getContents(), 
                        root.getProfile().getRepository().getEncryption());
                    
                    FileUtil.writeFile(packed, chunkCacheFile);                   
                }
            }
            
            
            // 2. Add the rest to the DB, and persist it
            if (chunkInfo != null) {
                // The last chunk holds the file checksum
                cf.setChecksum(chunkInfo.getFileChecksum()); 
            }

            cf.merge();
            
            
            // 3. Upload it
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Indexer: Added to DB. Now Q file {0} at uploader ...", file);
            }
            
            root.getProfile().getUploader().queue(cf);            
        }
        catch (Exception e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Could not index new file "+file+". IGNORING.", e);
            }
            
            return;
        } 
    }
    
    @Override
    public String toString() {
        return NewIndexRequest.class.getSimpleName()+
            "[" + "file=" + file + "]";
    }
    
}
