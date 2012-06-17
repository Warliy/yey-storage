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
package org.syncany.gui.tray;

import org.syncany.config.Config;
import org.syncany.Environment;
import org.syncany.exceptions.InitializationException;
import org.syncany.gui.tray.linux.LinuxTray;
import org.syncany.gui.tray.windows.WindowsTray;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Philipp C. Heckel
 */
public abstract class Tray {

    public enum StatusIcon { DISCONNECTED, UPDATING, UPTODATE };

    protected static Tray instance = null;
    protected static final Logger logger = Logger.getLogger(Tray.class.getSimpleName());
    protected static final Config config = Config.getInstance();
    protected static final Environment env = Environment.getInstance();    
    
    protected List<TrayEventListener> listeners;

    protected Tray() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Creating tray ...");
        }
        
        listeners = new ArrayList<TrayEventListener>();
    }

    public void addTrayEventListener(TrayEventListener listener) {
        listeners.add(listener);
    }

    public void removeTrayEventListener(TrayEventListener listener) {
        listeners.remove(listener);
    }

    public static Tray getInstance() {	
        if (instance != null) {
            return instance;
        }

        if (env.getOperatingSystem() == Environment.OperatingSystem.Linux) {
            instance = new LinuxTray();
            return instance;
        }

        else if (env.getOperatingSystem() == Environment.OperatingSystem.Windows) {
            instance = new WindowsTray();
            return instance;
        }

        throw new RuntimeException("Your OS is currently not supported: "+System.getProperty("os.name"));
    }

    protected void fireTrayEvent(TrayEvent event) {
        for (TrayEventListener l : listeners) {
            l.trayEventOccurred(event);
        }
    }

    public abstract void updateUI();
    public abstract void setStatusText(String msg);
    public abstract StatusIcon setStatusIcon(StatusIcon status);
    public abstract StatusIcon getStatusIcon();    
    public abstract void init() throws InitializationException;
    public abstract void destroy();
    public abstract void notify(String summary, String body, File imageFile);
}
