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
 * @author pheckel
 */
public class RackspaceConnection implements Connection {
    private String username;
    private String apiKey;
    private String container;
    private ResourceBundle resourceBundle;

    public RackspaceConnection() {
        resourceBundle = Config.getInstance().getResourceBundle();
    }
    
    
    @Override
    public PluginInfo getPluginInfo() {
        return Plugins.get(RackspacePluginInfo.ID);
    }
        
    @Override
    public TransferManager createTransferManager() {
        return new RackspaceTransferManager(this);
    }

    @Override
    public ConfigPanel createConfigPanel() {
        return new RackspaceConfigPanel(this);
    }
    
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    
    @Override
    public void load(ConfigNode node) throws ConfigException {
        // Mandatory
        username = node.getProperty("username");
        apiKey = node.getProperty("apikey");
        container = node.getProperty("container");

        if (username == null || apiKey == null || container == null) {
            throw new ConfigException("Rackspace connection properties must at least contain the parameters 'username', 'apikey' and 'container'.");
        }
    }

    @Override
    public void save(ConfigNode node) {
        node.setAttribute("type", getPluginInfo().getId());
        node.setProperty("username", username);
        node.setProperty("apikey", apiKey);
        node.setProperty("container", container);
    }
    
    @Override
    public String toString() {
        return RackspaceConnection.class.getSimpleName()
            + "[" + resourceBundle.getString("rackspace_username")  + "=" + username +
            ", " + resourceBundle.getString("rackspace_container") + "=" + container + "]";
    }
}
