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
package org.syncany.gui.tray.linux;

import org.syncany.gui.linux.NotifyRequest;
import org.syncany.gui.linux.UpdateMenuRequest;
import org.syncany.gui.linux.UpdateStatusIconRequest;
import org.syncany.exceptions.ConfigException;
import org.syncany.exceptions.InitializationException;
import org.syncany.gui.tray.Tray;
import java.io.File;
import java.util.logging.Level;
import org.syncany.gui.linux.ListenForTrayEventRequest;
import org.syncany.gui.linux.LinuxNativeClient;
import org.syncany.gui.linux.UpdateStatusTextRequest;
import org.syncany.gui.tray.TrayEvent;
import org.syncany.gui.tray.TrayEventListener;


/**
 *
 * @author Philipp C. Heckel
 */
public class LinuxTray extends Tray {
    private LinuxNativeClient nativeClient;
    private boolean initialized = false;
    private StatusIcon cachedStatus = StatusIcon.DISCONNECTED;
    
    public LinuxTray() {
        super();	     
    }

    @Override
    public void init() throws InitializationException {
        nativeClient = LinuxNativeClient.getInstance();
        nativeClient.init();
        addListener();

        initialized = true;	
        updateUI();	
    }

    @Override
    public synchronized void destroy() {
        nativeClient.destroy();
    }

    @Override
    public void setStatusText(String msg) {
        if (!initialized) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Cannot update status. Tray not initialized yet.");                
            }
            
            return;
        }
        
        nativeClient.send(new UpdateStatusTextRequest(msg));
    }
    
    @Override
    public StatusIcon setStatusIcon(StatusIcon status) {
        if (!initialized) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Cannot change icon. Tray not initialized yet.");                
            }
            
            return StatusIcon.DISCONNECTED;
        }

        if (cachedStatus != null && cachedStatus == status) {
            // Nothing to send!
            return cachedStatus;
        }
        
        nativeClient.send(new UpdateStatusIconRequest(status));

        cachedStatus = status;
        return cachedStatus;
    }
    
    @Override
    public StatusIcon getStatusIcon() {
        return cachedStatus;
    }    

    @Override
    public void notify(String summary, String body, File imageFile) {
        if (!initialized) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Cannot send notification. Tray not initialized yet.");                
            }
            
            return;
        }
	
        nativeClient.send(new NotifyRequest(summary, body, imageFile));
    }
    
    @Override
    public void updateUI() {
        if (!initialized) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Cannot update tray menu. Tray not initialized yet.");                
            }
            
            return;
        }

        nativeClient.send(new UpdateMenuRequest(config.getProfiles().list()));
    }
    
    public static void main(String[] args) throws ConfigException, InitializationException, InterruptedException {
        System.out.println("STARTED");

        //for (Entry<Object, Object> entry : System.getProperties().entrySet()) 
          //  System.out.println(entry.getKey() + " = "+entry.getValue());



        config.load();
        Tray tray = Tray.getInstance();

        tray.init();

        tray.notify("hallo", "test", null);
        //tray.setStatus(Status.UPDATING);
        tray.addTrayEventListener(new TrayEventListener() {

            @Override
            public void trayEventOccurred(TrayEvent event) {
            System.out.println(event);
            }
        });
        tray.setStatusIcon(StatusIcon.UPDATING);
        //System.out.println(FileUtil.showBrowseDirectoryDialog());

        while(true)
            Thread.sleep(1000);
	
    }

    private void addListener() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Object event = nativeClient.send(new ListenForTrayEventRequest());

                    if (event != null) {
                        fireTrayEvent((TrayEvent) event);
                    }
                }
            }
        }, "TrayListener").start();
    }

}
