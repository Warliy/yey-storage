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
package org.syncany.connection.plugins.s3;


import org.jets3t.service.security.ProviderCredentials;
import org.syncany.config.ConfigNode;
import org.syncany.connection.plugins.ConfigPanel;
import org.syncany.connection.plugins.PluginInfo;
import org.syncany.connection.plugins.Plugins;
import org.syncany.connection.plugins.TransferManager;
import org.syncany.exceptions.ConfigException;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.security.AWSCredentials;
import org.syncany.connection.plugins.rest.RestConnection;

/**
 *
 * @author oubou68, pheckel
 */
public class S3Connection extends RestConnection {   
    // cp. http://jets3t.s3.amazonaws.com/api/constant-values.html#org.jets3t.service.model.S3Bucket.LOCATION_ASIA_PACIFIC
    private String location;

    @Override
    public PluginInfo getPluginInfo() {
        return Plugins.get(S3PluginInfo.ID);
    }

    @Override
    public TransferManager createTransferManager() {
        return new S3TransferManager(this);
    }

    @Override
    public ConfigPanel createConfigPanel() {
        return new S3ConfigPanel(this);
    }
    
    @Override
    protected ProviderCredentials createCredentials() {       
        return new AWSCredentials(getAccessKey(), getSecretKey());
    }    

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public void load(ConfigNode node) throws ConfigException {
        super.load(node);
        location = node.getProperty("location", S3Bucket.LOCATION_US);
    }

    @Override
    public void save(ConfigNode node) {
        super.save(node);
        node.setProperty("location", location);
    }
    
    @Override
    public String toString() {
        return S3Connection.class.getSimpleName()
            + "[" + resourceBundle.getString("bucket") + "=" + bucket + ", " + resourceBundle.getString("location") + "=" + location+"]";
    }
}
