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
package org.syncany.config;

import org.syncany.connection.plugins.Connection;
import org.syncany.connection.plugins.PluginInfo;
import org.syncany.connection.plugins.Plugins;
import org.syncany.connection.plugins.TransferManager;
import org.syncany.exceptions.CacheException;
import org.syncany.exceptions.ConfigException;
import org.syncany.exceptions.NoRepositoryFoundException;
import org.syncany.exceptions.InvalidRepositoryException;
import org.syncany.exceptions.RepositoryFoundException;
import org.syncany.exceptions.StorageConnectException;
import org.syncany.exceptions.StorageException;
import org.syncany.Constants;
import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.syncany.repository.files.RepositoryFile;
import org.syncany.repository.files.StructuredFileList;

/**
 *
 * @author Philipp C. Heckel
 */
public final class Repository implements Configurable {
    private static final Config config = Config.getInstance();

    private Connection connection;
    private Encryption encryption;
    private Set<String> availableRemoteIds;

    /**
     * Maximum size of each (unencrypted) chunk in bytes. After encrypting
     * each chunk, their size may vary slightly.
     *
     * <p>Note: As of the current design, this value should not be changed. Doing
     * so means losing access to repositories with the previous chunk size!
     */
    private int chunkSize;
    
    private Date lastUpdate;
    private boolean changed;    
    private boolean connected;

    // New
    public Repository() {
        // Fressen
        connection = null; // Loaded or set dynamically!
        encryption = new Encryption();
        availableRemoteIds = new HashSet<String>();
        
        lastUpdate = null;
        changed = false;
        connected = false;
    }
 
    /**
     * Returns the chunk size in kilobytes
     * @return
     */
    public int getChunkSize() {
        return chunkSize;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Encryption getEncryption() {
        return encryption;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }

    public Set<String> getAvailableRemoteIds() {
        return availableRemoteIds;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }          
    
    public void update() throws CacheException, StorageConnectException, NoRepositoryFoundException, StorageException {
        TransferManager transfer = getConnection().createTransferManager();
        transfer.connect();
        
        update(transfer);
        
        transfer.disconnect();
    }
    
    public void update(TransferManager transfer) throws CacheException, StorageConnectException, NoRepositoryFoundException, StorageException {
        StructuredFileList fileList = new StructuredFileList(this, transfer.list(RepositoryFile.PREFIX));                    
        update(transfer, fileList);
    }
    
    /**
     * Update the local repository information from the remote repository.
     */
    public void update(TransferManager transfer, StructuredFileList fileList) throws CacheException, StorageConnectException, NoRepositoryFoundException, StorageException {
        // Create cache file for repo-file
        RepositoryFile repoFile = null;
        File localRepoFile = config.getCache().createTempFile(RepositoryFile.PREFIX);

        // Download repo file (if exists)
        try {
            // List & find newest repository file
            repoFile = fileList.getNewestRepositoryFile();
                        
            if (repoFile == null) {
                throw new NoRepositoryFoundException("No repository-* file found.");
            }
            
            /*if (!(lastUpdate != null && repoFile.getLastUpdate().after(lastUpdate))) {
                connected = true;
                System.out.println("hier!");
                return; // up-to-date
            }*/
            
            // Download!
            transfer.download(repoFile, localRepoFile);
        }
        catch (StorageException e) {
            throw new NoRepositoryFoundException(e);
        }

        // Read repo file
        try {            
            repoFile.read(localRepoFile);    
            
            connected = true;
            lastUpdate = repoFile.getLastUpdate();
        }
        catch (InvalidRepositoryException e) {
            throw e;
        }
        catch (Exception e) {
            throw new StorageException("Really weird exception: "+e, e);
        }
        finally {
            if (localRepoFile != null) {
                localRepoFile.delete();
            }
        }              
    }

    public void commit(boolean create) throws CacheException, RepositoryFoundException, StorageConnectException, StorageException {
        TransferManager transfer = getConnection().createTransferManager();
        transfer.connect();        
        
        commit(transfer, create);
        
        transfer.disconnect();
    }

    public void commit(TransferManager transfer, boolean create) throws CacheException, RepositoryFoundException, StorageConnectException, StorageException {
        StructuredFileList fileList = new StructuredFileList(this, transfer.list(RepositoryFile.PREFIX));                    
        commit(transfer, fileList, create);
    }
    
    /**
     * Creates a new repository at the given connection.
     * @throws StorageException
     */
    public void commit(TransferManager transfer, StructuredFileList fileList, boolean create) throws CacheException, RepositoryFoundException, StorageConnectException, StorageException {
        
        try {
            if (create && !fileList.getRepoFiles().isEmpty()) {
                throw new RepositoryFoundException("Repository already initialized!");
            }

            Date newLastCommit = new Date();
            RepositoryFile repoFile = new RepositoryFile(this, newLastCommit);
            File localRepoFile = config.getCache().createTempFile(repoFile.getName());

            repoFile.write(localRepoFile);

            // Upload
            transfer.upload(localRepoFile, repoFile);
            
            connected = true;
            changed = false;
            lastUpdate = newLastCommit;
        }
        catch (RepositoryFoundException e) {
            throw e;
        }
        catch (Exception e) {
            throw new StorageException(e);
        }           
    }

    @Override
    public void load(ConfigNode node) throws ConfigException {
        if (node == null) {
            throw new ConfigException("Missing repository.");
        }

        try {            
            chunkSize = node.getInteger("chunksize", Constants.DEFAULT_CHUNK_SIZE);

            // Connection
            ConfigNode connectionNode = node.findChildByXPath("connection");

            if (connectionNode == null) {
                throw new ConfigException("No connection found in repository");
            }

            PluginInfo connectionPlugin = Plugins.get(connectionNode.getAttribute("type"));

            if (connectionPlugin == null) {
                throw new ConfigException("Unknown repository plugin '"+connectionNode.getAttribute("type")+"' in repository.");
            }

            connection = connectionPlugin.createConnection();
            connection.load(connectionNode);

            // Encryption
            ConfigNode encNode = node.findChildByXPath("encryption");

            if (encNode == null) {
                throw new ConfigException("No encryption found in repository");
            }

            encryption.load(encNode);
        }
        catch (Exception e) {
            throw new ConfigException("Unable to load repository: "+node+", error: "+e, e);
        }
    }

    @Override
    public void save(ConfigNode node) {
        node.setProperty("chunksize", chunkSize);

        connection.save(node.findOrCreateChildByXpath("connection", "connection"));
        encryption.save(node.findOrCreateChildByXpath("encryption", "encryption"));
    }
}
