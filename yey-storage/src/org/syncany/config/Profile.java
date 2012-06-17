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

import org.syncany.exceptions.ConfigException;
import org.syncany.index.Indexer;
import org.syncany.repository.Uploader;
import org.syncany.watch.local.LocalWatcher;
import org.syncany.watch.remote.RemoteWatcher;

/**
 *
 * @author Philipp C. Heckel
 */
public class Profile implements Configurable {   
    public static String tagName() { return "profile"; }
    public static String xpath(int id) { return "profile[@id='"+id+"']"; };

    private static final Indexer indexer = Indexer.getInstance();
    private static final LocalWatcher localWatcher = LocalWatcher.getInstance();
    
    private boolean active;
    
    private int id;
    private boolean enabled;
    private String name;
    private Repository repository;
    private Folders folders;
    
    private Uploader uploader;
    private RemoteWatcher remoteWatcher;

    public Profile() {
        active = false;
        
        id = -1;
        enabled = true;
        name = "(unknown)";
        repository = new Repository();
        folders = new Folders(this);
        
        uploader = new Uploader(this);
        remoteWatcher = new RemoteWatcher(this);
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void setActive(boolean active) {
        if (active == isActive()) {
            return;
        }
        
        // Activate
        if (active) {
            // Synchronously index files and add file system watches
            indexer.index(this);            
            localWatcher.watch(this);

            // Start threads
            uploader.start();
            remoteWatcher.start();
            
            this.active = true;
        }
        
        // Deactivate
        else {
            localWatcher.unwatch(this);
            
            uploader.stop();
            remoteWatcher.stop();
            
            this.active = active;
        }               
    }    
    
    public Folders getFolders() {
        return folders;
    }

    public int getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Repository getRepository() {
        return repository;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFolders(Folders folders) {
        this.folders = folders;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public RemoteWatcher getRemoteWatcher() {
        return remoteWatcher;
    }

    public Uploader getUploader() {
        return uploader;
    }

    @Override
    public void load(ConfigNode node) throws ConfigException {
        try {
            id = Integer.parseInt(node.getAttribute("id"));
            enabled = node.getBoolean("enabled");
            name = node.getProperty("name");

            // Repo
            repository = new Repository();
            repository.load(node.findChildByName("repository"));
                        
            // Folders
            folders = new Folders(this);
            folders.load(node.findChildByXPath("folders"));
            
            // Remote IDs
            for (Folder folder : folders.list()) {
                repository.getAvailableRemoteIds().add(folder.getRemoteId());
            }
        }
        catch (Exception e) {
            throw new ConfigException("Unable to load profile: "+e, e);
        }
    }

    @Override
    public void save(ConfigNode node) {
        node.setAttribute("id", id);
        node.setProperty("enabled", enabled);
        node.setProperty("name", name);

        // Repo
        repository.save(node.findOrCreateChildByXpath("repository", "repository"));

        // Folders
        folders.save(node.findOrCreateChildByXpath("folders", "folders"));
    }      
}
