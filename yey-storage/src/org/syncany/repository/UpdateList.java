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

import java.util.logging.Level;
import org.syncany.repository.files.UpdateFile;
import org.syncany.db.CloneClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.syncany.config.Profile;
import org.syncany.db.DatabaseHelper;

/**
 * Encapsules the updates grouped by clients.
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class UpdateList {
    private static final Logger logger = Logger.getLogger(UpdateList.class.getSimpleName());
    
    private Profile profile;
    private DatabaseHelper db;
    
    /**
     * (file id, max version)
     */
    private Map<Long, Long> fileIdMaxVersionMap;
    private Map<CloneClient, UpdateFile> remoteUpdateFiles;

    public UpdateList(Profile profile) {
        this.profile = profile;
        this.db = DatabaseHelper.getInstance();
        
        this.remoteUpdateFiles = new HashMap<CloneClient, UpdateFile>();
        this.fileIdMaxVersionMap = new HashMap<Long, Long>();
    }

    public void addRemoteUpdateFile(CloneClient client, UpdateFile updateFile) {
        logger.info("Adding update file to temp. DB [" + client.getMachineName() + ", " + updateFile.getName() + "] ...");

        // Add to list (for the record)
        remoteUpdateFiles.put(client, updateFile);
    }

    /**
     * Looks at all the available remote update files and generates
     * a single update list. The generated list has correct dependencies (causally).
     * @return
     */
    public List<Update> generateUpdateList() {
        Map<Long, String> fileIdClientNameMap = new HashMap<Long, String>();
        List<Update> resultList = new ArrayList<Update>();

        for (Map.Entry<CloneClient, UpdateFile> e : remoteUpdateFiles.entrySet()) {
            CloneClient ufClient = e.getKey();
            UpdateFile uf = e.getValue();

            for (Update u : uf.getUpdates()) {
                if (resultList.contains(u)) {
                    continue;
                }

                String responsibleClientForFile = fileIdClientNameMap.get(u.getFileId());

                // If previous versions of this file used this client
                // also use this update
                if (ufClient.getMachineName().equals(responsibleClientForFile)) {
                    //logger.info("File "+u.getFileId()+", version: "+u.getVersion()+": Using file history of client '"+fileIdClientNameMap.get(u.getFileId())+"'");

                    fileIdMaxVersionMap.put(u.getFileId(), u.getVersion());
                    resultList.add(u);
                    continue;
                } 
                else if (responsibleClientForFile != null) {
                    //logger.info("File "+u.getFileId()+", version: "+u.getVersion()+": Ignoring this update; Client '"+fileIdClientNameMap.get(u.getFileId())+"' is responsible!");
                    continue;
                }

                // No responsible client: Determine it!

                // TODO this assumes that the update file always delivers correct data
                /*boolean parentExists =
                        addedVersionIds.contains(u.getParentFileVersionId())
                        || parentExistsInDB(u);

                if (!parentExists) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "File {0}, version: {1}: Parent {2}, v{3} does not exist locally or in updates.", new Object[]{u.getFileId(), u.getVersion(), u.getParentFileId(), u.getParentFileVersion()});
                    }
                    
                    continue;
                }*/

                // Compare existing versions
                Update newestUpdate = u;

                for (UpdateFile uf2 : remoteUpdateFiles.values()) {
                    Update u2 = uf2.getFileUpdate(u.getFileId(), u.getVersion());

                    if (u2 == null) {
                        continue;
                    }

                    // Parent must exist
                    // TODO this assumes that the update file always delivers correct data
                    /*boolean cParentExists =
                            addedVersionIds.contains(u2.getParentFileVersionId())
                            || parentExistsInDB(u2);

                    if (!cParentExists) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "u2 File {0}, version: {1}: Parent {2}, v{3} does not exist locally or in updates.", new Object[]{u2.getFileId(), u2.getVersion(), u2.getParentFileId(), u2.getParentFileVersion()});
                        }
                        
                        continue;
                    }*/

                    if (u2.getUpdated().before(u.getUpdated())) {
                        newestUpdate = u2;
                    }
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "File {0}, version: {1}: Using file history of client ''{2}''", new Object[]{newestUpdate.getFileId(), newestUpdate.getVersion(), ufClient.getMachineName()});
                }
                
                fileIdMaxVersionMap.put(newestUpdate.getFileId(), newestUpdate.getVersion());
                fileIdClientNameMap.put(newestUpdate.getFileId(), ufClient.getMachineName());
                resultList.add(newestUpdate);
            }
        }

        return resultList;
    }

    public Long getMaxVersion(Long fileId) {
        return fileIdMaxVersionMap.get(fileId);
    }

    public Map<CloneClient, UpdateFile> getRemoteUpdateFiles() {
        return remoteUpdateFiles;
    }

    /*public static void main(String[] a) throws IOException {
        UpdateFile u1 = new UpdateFile(new RemoteFile("update-client1-111"));
        UpdateFile u2 = new UpdateFile(new RemoteFile("update-client2-222"));

        u1.readFromLocalFile(new File("/home/pheckel/Coding/syncany/syncany/test/updatefiles/update-client1-111.csv"), false);
        u2.readFromLocalFile(new File("/home/pheckel/Coding/syncany/syncany/test/updatefiles/update-client2-222.csv"), false);

        UpdateList um = new UpdateList();

        um.addUpdateFile(new CloneClient(u1.getClientName(), 1), u1);
        um.addUpdateFile(new CloneClient(u2.getClientName(), 1), u2);

        System.err.println("OUTPUT:");
        for (Update u : um.generateUpdateList()) {
            System.err.println(u);
        }
    }*/

    private boolean parentExistsInDB(Update u) {        
        return null != db.getFileOrFolder(profile, u.getParentFileId(), u.getParentFileVersion());        
    }
}
