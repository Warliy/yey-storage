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
package org.syncany.connection.plugins.gs;

import java.util.ResourceBundle;

import org.jets3t.service.security.ProviderCredentials;
import org.syncany.connection.plugins.rest.RestConnection;
import org.jets3t.service.security.GSCredentials;
import org.syncany.config.Config;
import org.syncany.connection.plugins.ConfigPanel;
import org.syncany.connection.plugins.PluginInfo;
import org.syncany.connection.plugins.Plugins;
import org.syncany.connection.plugins.TransferManager;

/**
 *
 * @author Philipp C. Heckel
 */
public class GsConnection extends RestConnection {
 // http://jets3t.s3.amazonaws.com/toolkit/code-samples.html#gs-connect
	private ResourceBundle resourceBundle;

    public GsConnection() {
        resourceBundle = Config.getInstance().getResourceBundle();
    }

    @Override
    public PluginInfo getPluginInfo() {
        return Plugins.get(GsPluginInfo.ID);
    }

    @Override
    public TransferManager createTransferManager() {
        return new GsTransferManager(this);
    } 

    @Override
    public ConfigPanel createConfigPanel() {
        return new GsConfigPanel(this); 
    }

    @Override
    protected ProviderCredentials createCredentials() {
        return new GSCredentials(accessKey, secretKey);
    }
    
    @Override
    public String toString() {
        return GsConnection.class.getSimpleName()
            + "[" + resourceBundle.getString("gs_bucket") + "=" + bucket + "]";
    }
}
