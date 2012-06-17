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
package org.syncany.connection.plugins.rackspace;

import com.rackspacecloud.client.cloudfiles.FilesClient;
import com.rackspacecloud.client.cloudfiles.FilesObject;
import java.io.InputStream;
import org.syncany.connection.plugins.AbstractTransferManager;
import org.syncany.exceptions.StorageConnectException;
import org.syncany.repository.files.RemoteFile;
import org.syncany.exceptions.StorageException;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.util.FileUtil;

/**
 *
 * @author oubou68, pheckel
 */
public class RackspaceTransferManager extends AbstractTransferManager {
    private static final String AUTH_URL = "https://auth.api.rackspacecloud.com/v1.0";
    private static final int CONNECTION_TIMEOUT = 5000;
    
    private FilesClient client;

    public RackspaceTransferManager(RackspaceConnection connection) {
        super(connection);
        
        client = new FilesClient(connection.getUsername(), connection.getApiKey());
        client.setAuthenticationURL(AUTH_URL);        
        client.setConnectionTimeOut(CONNECTION_TIMEOUT);        
    }

    @Override
    public RackspaceConnection getConnection() {
        return (RackspaceConnection) super.getConnection();
    }

    @Override
    public void connect() throws StorageConnectException {
        if (client.isLoggedin()) {
            return;
        }
        
        try {
            client.login();
        }
        catch (Exception ex) {
            Logger.getLogger(RackspaceTransferManager.class.getName()).log(Level.SEVERE, null, ex);
            throw new StorageConnectException(ex);
        }             
    }
    
    @Override
    public void disconnect() throws StorageException {
        // Fressen.
    }

    @Override
    public void download(RemoteFile remoteFile, File localFile) throws StorageException {
        connect();
        File tempFile = null;

        try {
            InputStream is = client.getObjectAsStream(getConnection().getContainer(), remoteFile.getName());

            // Save to temp file
            tempFile = config.getCache().createTempFile(remoteFile.getName());
            FileUtil.writeFile(is, tempFile);

            // Rename temp file
            tempFile.renameTo(localFile);
        } 
        catch (Exception ex) {
            if (tempFile != null) {
                tempFile.delete();
            }

            throw new StorageException("Unable to download file '"+remoteFile.getName(), ex);
        } 
    }

    @Override
    public void upload(File localFile, RemoteFile remoteFile) throws StorageException {
        connect();
                	
        try {
            // Check if exists
            Collection<RemoteFile> obj = list(remoteFile.getName()).values();

            if (obj != null && !obj.isEmpty()) {
                return;
            }
	    
            // Upload
            client.storeObjectAs(getConnection().getContainer(), localFile, "application/x-syncany", remoteFile.getName());
        } 
        catch (Exception ex) {
            Logger.getLogger(RackspaceTransferManager.class.getName()).log(Level.SEVERE, null, ex);
            throw new StorageException(ex);
        }
    }

    @Override
    public void delete(RemoteFile remoteFile) throws StorageException {
        connect();

        try {
            client.deleteObject(getConnection().getContainer(), remoteFile.getName());
        } 
        catch (Exception ex) {
            Logger.getLogger(RackspaceTransferManager.class.getName()).log(Level.SEVERE, null, ex);
            throw new StorageException(ex);
        }
    }

    @Override
    public Map<String, RemoteFile> list() throws StorageException {
        connect();
		return list("");        
    }

    @Override
    public Map<String, RemoteFile> list(String namePrefix) throws StorageException {
        connect();
	        
        try {
            List<FilesObject> objects = client.listObjectsStartingWith(getConnection().getContainer(), namePrefix, null, -1, null);            
            Map<String, RemoteFile> list = new HashMap<String, RemoteFile>();

            for (FilesObject obj : objects) {
                list.put(obj.getName(), new RemoteFile(obj.getName(), -1, obj));
            }
	    
            return list;

        } 
        catch (Exception ex) {
            Logger.getLogger(RackspaceTransferManager.class.getName()).log(Level.SEVERE, null, ex);
            throw new StorageException(ex);
        }
    }
}
