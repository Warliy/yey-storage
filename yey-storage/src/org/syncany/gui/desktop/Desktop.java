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
package org.syncany.gui.desktop;

import org.syncany.config.Config;
import org.syncany.exceptions.ConfigException;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Syncany file manager extension is strongly based on the Dropbox file
 * manager extension. It uses two servers to communicate with the extension:
 * 
 * <p>The {@link CommandServer} answers queries by file manager, e.g. regarding
 * emblems and popup-menu entries.
 * 
 * <p>The {@link TouchServer} can send 'touch' queries to the file manager
 * extension. A 'touch' invalidates the emblem-status of a file and forces
 * the file manager to re-query the information using the command server.
 * 
 * @author Philipp C. Heckel
 * @see <a href="https://www.dropbox.com/downloading?os=lnx">Dropbox Nautilus
 *      extension</a> (dropbox.com)
 */
public class Desktop {
    private static final Logger logger = Logger.getLogger(Desktop.class.getSimpleName());
    private static Desktop instance;
    
    private TouchServer touchServ;
    private CommandServer commandServ;
 
    private Desktop() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Creating desktop integration ...");
        }
        
        touchServ = new TouchServer();
        commandServ = new CommandServer();
    }
    
    public synchronized static Desktop getInstance() {
        if (instance == null) {
            instance = new Desktop();
        }
        
        return instance;
    }

    public void start() {
        new Thread(touchServ, "Touch Server").start();
        new Thread(commandServ, "Command Server").start();
    }

    public void touch(File file) {
        if (!touchServ.isRunning()) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Warning: Touch server NOT RUNNING. Ignoring touch to {0}", file);
            }
            
            return;
        }

        touchServ.touch(file);
    }

    public static void main(String[] a) throws InterruptedException, ConfigException {
        Config.getInstance().load();
        new Desktop().start();

        while (true) Thread.sleep(1000);
    }

}
