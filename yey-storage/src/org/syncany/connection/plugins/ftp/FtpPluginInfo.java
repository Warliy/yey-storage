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
package org.syncany.connection.plugins.ftp;

import java.util.ResourceBundle;

import org.syncany.config.Config;
import org.syncany.connection.plugins.Connection;
import org.syncany.connection.plugins.PluginInfo;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class FtpPluginInfo extends PluginInfo {
    public static final String ID = "ftp";
    private ResourceBundle resourceBundle;

    public FtpPluginInfo() {
         resourceBundle = Config.getInstance().getResourceBundle();
    }        
    
    @Override
    public String getId() {
        return ID;
    }    
    
    @Override
    public String getName() {
        return "FTP";
    }

    @Override
    public Integer[] getVersion() {
        return new Integer[] { 0, 1 };
    }

    @Override
    public String getDescripton() {
        return resourceBundle.getString("use_ftp_folder");
    }

    @Override
    public Connection createConnection() {
        return new FtpConnection();
    }
}
