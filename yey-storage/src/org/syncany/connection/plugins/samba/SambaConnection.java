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
package org.syncany.connection.plugins.samba;

import java.util.ResourceBundle;

import org.syncany.config.Config;
import org.syncany.config.ConfigNode;
import org.syncany.connection.plugins.ConfigPanel;
import org.syncany.connection.plugins.Connection;
import org.syncany.connection.plugins.PluginInfo;
import org.syncany.connection.plugins.Plugins;
import org.syncany.connection.plugins.TransferManager;
import org.syncany.exceptions.ConfigException;

/**
 *
 * @author Philipp C. Heckel
 */
public class SambaConnection implements Connection {
    private String root;
    private ResourceBundle resourceBundle;

    public SambaConnection() {
        resourceBundle = Config.getInstance().getResourceBundle();
    }
    
    @Override
    public PluginInfo getPluginInfo() {
        return Plugins.get(SambaPluginInfo.ID);
    }

    @Override
    public TransferManager createTransferManager() {
        return new SambaTransferManager(this);
    }

    @Override
    public ConfigPanel createConfigPanel() {
        return new SambaConfigPanel(this);
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        if (!root.endsWith("/")) {
            root = root+"/";
        }

        this.root = root;
    }
    
    @Override
    public void load(ConfigNode node) throws ConfigException {
        // Mandatory
        String strFolder = node.getProperty("root");

        if (strFolder == null) {
            throw new ConfigException("Samba connection must at least contain the 'root'.");
        }

        root = strFolder;	
    }

    @Override
    public void save(ConfigNode node) {
        node.setAttribute("type", SambaPluginInfo.ID);
        node.setProperty("root", root);
    }
    
    @Override
    public String toString() {
        return SambaConnection.class.getSimpleName()
        + "[" + resourceBundle.getString("samba_root") + "=" + root + "]";
    }	      
}
