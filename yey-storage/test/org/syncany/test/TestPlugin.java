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
package org.syncany.test;

import java.io.File;
import java.util.Map;
import org.syncany.config.Config;
import org.syncany.connection.plugins.PluginInfo;
import org.syncany.connection.plugins.Plugins;
import org.syncany.connection.plugins.TransferManager;
import org.syncany.connection.plugins.box.BoxConnection;
import org.syncany.exceptions.ConfigException;
import org.syncany.exceptions.LocalFileNotFoundException;
import org.syncany.exceptions.StorageConnectException;
import org.syncany.exceptions.StorageException;
import org.syncany.repository.files.RemoteFile;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class TestPlugin {
    public static void main(String[] args) throws StorageConnectException, LocalFileNotFoundException, StorageException, ConfigException {
        Config.getInstance().load();
        
        PluginInfo plugin = Plugins.get("box");
        BoxConnection c = (BoxConnection) plugin.createConnection();
        
        c.setApiKey("...");
        c.setFolderId("85344946");
        c.setTicket("...");
        
        TransferManager tm = c.createTransferManager();
        
        tm.connect();
        //tm.upload(new File("/etc/hosts"), new RemoteFile("hosts"));
        tm.download(new RemoteFile("hosts"), new File("/home/pheckel/hosts"));
        tm.delete(new RemoteFile("hosts"));
        Map<String, RemoteFile> list = tm.list();
        
        for (RemoteFile f : list.values()) {
            System.out.println(f);

        }

    }
}
