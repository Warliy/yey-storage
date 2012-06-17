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
import java.io.File;

/**
 *
 * @author Philipp C. Heckel
 */
public class Folder implements Configurable {
    private Profile profile;
    
    private boolean active;
    private String remoteId;
    private File localFile;

    public Folder() {
        this(null);        
    }
    
    public Folder(Profile profile) {      
        this.profile = profile;
        
        this.active = true;
        this.remoteId = "";
        this.localFile = null;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }        

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setLocalFile(File localFile) {
        this.localFile = localFile;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }
    
    public File getLocalFile() {
        return localFile;
    }

    public String getRemoteId() {
        return remoteId;
    }

    @Override
    public void load(ConfigNode node) throws ConfigException {
        try {
            if (node.hasProperty("active")) {
                active = node.getBoolean("active");
            }

            remoteId = node.getProperty("remote");
            localFile = node.getFile("local");
        }
        catch (Exception e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public void save(ConfigNode node) {
        node.setProperty("active", active);
        node.setProperty("remote", remoteId);
        node.setProperty("local", localFile.getAbsolutePath());
    }
}
