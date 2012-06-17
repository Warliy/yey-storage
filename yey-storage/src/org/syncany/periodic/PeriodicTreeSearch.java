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
package org.syncany.periodic;

import org.syncany.index.Indexer;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.config.Config;
import org.syncany.config.Folder;
import org.syncany.config.Profile;

/**
 * Prevents missed updates by regularly searching the whole
 * file tree for new or altered files.
 *
 * TODO Implement this
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PeriodicTreeSearch {
    private static final Logger logger = Logger.getLogger(PeriodicTreeSearch.class.getSimpleName());    
    private static final int SEARCH_INTERVAL = 60;
    
    private Thread worker;        
    
    public PeriodicTreeSearch() {
        // Nothing.
    }

    public synchronized void start() {
        if (worker != null) {
            return;
        }
        
        worker = new Thread(new Worker(), "TreeSearch");
        worker.setPriority(Thread.MIN_PRIORITY);
        
        worker.start();
    }    

    public synchronized void stop() {
        if (worker == null) {
            return;
        }       
        
        worker.interrupt();
        worker = null;        
    }
    
    private class Worker implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "Started periodic tree search ...");
                    }

                    for (Profile profile : Config.getInstance().getProfiles().list()) {
                        if (!profile.isEnabled()) {
                            continue;
                        }

                        if (logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO, "Checking profile ''{0}'' ...", profile.getName());
                        }
                        
                        Indexer.getInstance().index(profile);
                    }
                    
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "Finished periodic tree search. Now sleeping {0} seconds.", SEARCH_INTERVAL);
                    }

                    Thread.sleep(SEARCH_INTERVAL*1000);
                }
            }
            catch (InterruptedException e) {
                // Nothing.
            }
        }        
    }
    
}
