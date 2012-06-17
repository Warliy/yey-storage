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
package org.syncany.gui.linux;

import java.util.ArrayList;
import java.util.List;

import org.syncany.config.Folder;
import org.syncany.config.Profile;

/**
 *
 * @author pheckel
 */
public class UpdateMenuRequest implements Request {
    private List<ProfileProxy> profiles;

    public UpdateMenuRequest(List<Profile> profiles) {
        this.profiles = new ArrayList<ProfileProxy>();

        for (Profile profile : profiles) {
            ProfileProxy profileProxy = new ProfileProxy();	    
            profileProxy.setName(profile.getName());

            List<FolderProxy> folderProxies = new ArrayList<FolderProxy>();

            for (Folder folder : profile.getFolders().list()) 
            folderProxies.add(new FolderProxy(folder.getRemoteId(), folder.getLocalFile()));

            profileProxy.setFolders(folderProxies);
            this.profiles.add(profileProxy);
        }
    }

    public List<ProfileProxy> getProfiles() {
        return profiles;
    }

    @Override
    public String toString() {
        return "{\"request\":\"UpdateMenuRequest\",\"profiles\":" + profiles + "}";
    }

    @Override
    public Object parseResponse(String responseLine) {
        return null;
    }
}
