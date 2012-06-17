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
package org.syncany.repository.files;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.syncany.config.Config;
import org.syncany.config.Repository;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class StructuredFileList {
    private Config config;
    private Repository repository;
    private Map<String, RemoteFile> fileList;
    
    private TreeMap<Long, RepositoryFile> repoFiles;
    
    private TreeMap<Long, UpdateFile> localUpdateFiles;
    private TreeMap<Long, ProfileFile> localProfileFiles;
    private TreeMap<Long, ImageFile> localImageFiles;
    
    private Map<String, UpdateFile> remoteUpdateFiles;
    private Map<String, ProfileFile> remoteProfileFiles;
    private Map<String, ImageFile> remoteImageFiles;

    public StructuredFileList(Repository repository, Map<String, RemoteFile> fileList) {
        this.config = Config.getInstance();
        this.repository = repository;
        this.fileList = fileList;
        
        this.repoFiles = new TreeMap<Long, RepositoryFile>();
        
        this.localUpdateFiles = new TreeMap<Long, UpdateFile>();
        this.localProfileFiles = new TreeMap<Long, ProfileFile>();
        this.localImageFiles = new TreeMap<Long, ImageFile>();
        
        this.remoteUpdateFiles = new HashMap<String, UpdateFile>();
        this.remoteProfileFiles = new HashMap<String, ProfileFile>();
        this.remoteImageFiles = new HashMap<String, ImageFile>();
        
        parseList();
    }

    private void parseList() {
        for (RemoteFile rf : fileList.values()) {
            try {
                // Is repository file?
                if (rf.getName().startsWith(RepositoryFile.PREFIX)) {
                    RepositoryFile repoFile = RepositoryFile.createRepositoryFile(repository, rf);                    
                    repoFiles.put(repoFile.getLastUpdate().getTime(), repoFile);
                }
                        
                // Is update file?
                else if (rf.getName().startsWith(UpdateFile.PREFIX)) {
                    UpdateFile newUpdateFile = UpdateFile.createUpdateFile(repository, rf);
                    
                    // Local?
                    if (newUpdateFile.getMachineName().equals(config.getMachineName())) {
                        localUpdateFiles.put(newUpdateFile.getLastUpdate().getTime(), newUpdateFile);
                    }
                    
                    // Remote?
                    else {
                        UpdateFile oldUpdateFile = remoteUpdateFiles.get(newUpdateFile.getMachineName());

                        // Add if newer
                        if (oldUpdateFile == null || newUpdateFile.getLastUpdate().after(oldUpdateFile.getLastUpdate())) {
                            remoteUpdateFiles.put(newUpdateFile.getMachineName(), newUpdateFile);
                        }
                    }
                }   
                        
                // Is profile file?
                else if (rf.getName().startsWith(ProfileFile.PREFIX)) {
                    ProfileFile newProfileFile = ProfileFile.createProfileFile(repository, rf);
                    
                    // Local?
                    if (newProfileFile.getMachineName().equals(config.getMachineName())) {
                        localProfileFiles.put(newProfileFile.getLastUpdate().getTime(), newProfileFile);
                    }
                    
                    // Remote?
                    else {
                        ProfileFile oldProfileFile = remoteProfileFiles.get(newProfileFile.getMachineName());

                        // Add if newer
                        if (oldProfileFile == null || newProfileFile.getLastUpdate().after(oldProfileFile.getLastUpdate())) {
                            remoteProfileFiles.put(newProfileFile.getMachineName(), newProfileFile);
                        }
                    }
                }

                // Is image file?
                else if (rf.getName().startsWith(ImageFile.PREFIX)) {
                    ImageFile newImageFile = ImageFile.createImageFile(repository, rf);
                    
                    // Local?
                    if (newImageFile.getMachineName().equals(config.getMachineName())) {
                        localImageFiles.put(newImageFile.getLastUpdate().getTime(), newImageFile);
                    }
                    
                    // Remote?
                    else {
                        ImageFile oldImageFile = remoteImageFiles.get(newImageFile.getMachineName());

                        // Add if newer
                        if (oldImageFile == null || newImageFile.getLastUpdate().after(oldImageFile.getLastUpdate())) {
                            remoteImageFiles.put(newImageFile.getMachineName(), newImageFile);
                        }
                    }
                }                
            }
            catch (Exception e) {
                // Ignore file.
                continue;
            }
        }
                
    }


    public TreeMap<Long, ImageFile> getLocalImageFiles() {
        return localImageFiles;
    }

    public TreeMap<Long, ProfileFile> getLocalProfileFiles() {
        return localProfileFiles;
    }

    public TreeMap<Long, UpdateFile> getLocalUpdateFiles() {
        return localUpdateFiles;
    }

    public Map<String, ImageFile> getRemoteImageFiles() {
        return remoteImageFiles;
    }

    public Map<String, ProfileFile> getRemoteProfileFiles() {
        return remoteProfileFiles;
    }

    public Map<String, UpdateFile> getRemoteUpdateFiles() {
        return remoteUpdateFiles;
    }        

    public TreeMap<Long, RepositoryFile> getRepoFiles() {
        return repoFiles;
    }
    
    public RepositoryFile getNewestRepositoryFile() {
        if (repoFiles.isEmpty()) {
            return null;
        }
        
        return repoFiles.lastEntry().getValue();
    }
    
    public ProfileFile getNewestProfileFile() {
        if (localProfileFiles.isEmpty()) {
            return null;
        }
        
        return localProfileFiles.lastEntry().getValue();
    }
    
    public ImageFile getNewestImageFile() {
        if (localImageFiles.isEmpty()) {
            return null;
        }
        
        return localImageFiles.lastEntry().getValue();
    }    
    
}
