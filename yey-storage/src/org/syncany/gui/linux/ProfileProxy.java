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

import java.io.Serializable;
import java.util.List;
import org.apache.commons.lang.StringUtils;


/**
 *
 * @author pheckel
 */
public class ProfileProxy implements Serializable {
    private String name;
    private List<FolderProxy> folders;

    public ProfileProxy() {
    }

    public void setFolders(List<FolderProxy> folders) {
        this.folders = folders;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<FolderProxy> getFolders() {
        return folders;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        String eName = StringUtils.replaceChars(name, "\"", "\\\"");
        return "{\"name\":\"" + eName + "\",\"folders\":"+folders+"}";
    }       
}