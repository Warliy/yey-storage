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
package org.syncany;

import org.syncany.config.Config;
import org.syncany.config.Profile;
import org.syncany.exceptions.ConfigException;
import org.syncany.exceptions.InitializationException;
import org.syncany.gui.settings.SettingsDialog;
import org.syncany.gui.tray.Tray;
import org.syncany.gui.tray.TrayEvent;
import org.syncany.gui.tray.TrayEventListener;
import org.syncany.index.Indexer;
import org.syncany.watch.remote.ChangeManager;
import org.syncany.repository.Uploader;
import org.syncany.gui.desktop.Desktop;
import org.syncany.watch.local.LocalWatcher;
import java.awt.EventQueue;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.syncany.gui.wizard.WizardDialog;
import org.syncany.periodic.PeriodicTreeSearch;
import org.syncany.util.FileUtil;
import org.syncany.util.RollingChecksum;

/**
 * Represents the application.
 * 
 * <ul>
 * <li>{@link Watcher}: Listens to changes of the file system in the given local
 *     sync folder. Passes changes to the indexer.
 * <li>{@link Indexer}: Reads local files and compares them to the versions in
 *     local database. If necessary, it creates DB versions of new or altered files
 *     and passes them to the storage manager for upload.
 * <li>{@link Uploader}: Uploads and downloads remote files from the shared
 *     storage. Receives upload requests by the {@link Indexer}, and download
 *     requests by the {@link PeriodicStorageMonitor}.
 * <li>{@link PeriodicStorageMonitor}: Checks the online storage for changes
 *     in regular intervals, then downloads changes and notifies the {@link ChangeManager}.
 * </ul>
 *
 * <p>General application To-Do list: Focus: <b>GET IT TO WORK!</b>
 * <ul>
 * <li>TODO [high] adjust separator for Win/Linux platforms: e.g. transform "\" to "/" EVERYWHERE!
 * </ul>
 *
 * <p>Medium priority To-Do list:
 * <ul>
 * <li>TODO [medium] Connectivity management: Handle broken connections in every single class
 * <li>TODO [medium] Make checksum long value instead of int, cp. {@link RollingChecksum}
 * </ul>
 *
 * <p>Low priority To-Do list:
 * <ul>
 * <li>TODO [low] make platform specific file manager integration (windows explorer, mac finder, ...)
 * <li>TODO [low] cache: implement a cache-cleaning functionality for the local and online storage.
 * <li>TODO [low] cache: implement a cache-size parameter for the local cache.
 * </ul>
 *
 * <p>Wish list:
 * <ul>
 * <li>TODO [wish] strategy for down/uploading : FIFO, larget first, ...
 * </ul>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Application {
    private static final Logger logger = Logger.getLogger(Application.class.getSimpleName());
    
    private Config config;
    private Desktop desktop;
    private Indexer indexer;
    private LocalWatcher localWatcher;
    private Tray tray;
    private PeriodicTreeSearch periodic;
    private SettingsDialog settingsDialog;

    public Application() {
        // Nothing.
    }

    public void start() throws InitializationException {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Starting Application ...");
        }
        
        // Do NOT change the order of these method calls!
        // They strongly depend on each other.        
        initDependencies();
        initUI();       

        
        // This is done in a thread, so the application can finish 
        // initializing. The profile stuff is separate from the rest!        
        new Thread(new Runnable() {
            @Override
            public void run() {
                // a. Launch first time wizard
                if (config.getProfiles().list().isEmpty()) { 
                    initFirstTimeWizard();
                }

                // b. Activate profiles (Index files, then start local/remote watcher)        
                else {
                    initProfiles();
                }  
                
                // Start the rest
                indexer.start();
                localWatcher.start();
                //periodic.start();
            }
        }, "InitProfiles").start();
    }
    
    private void initDependencies() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Instantiating dependencies ...");
        }
          
        config = Config.getInstance();        
        desktop = Desktop.getInstance();
        indexer = Indexer.getInstance();
        localWatcher = LocalWatcher.getInstance();
        tray = Tray.getInstance();     
        periodic = new PeriodicTreeSearch();
    }

    private void doShutdown() {
        logger.info("Shutting down ...");

        tray.destroy();
        indexer.stop();
        localWatcher.stop();
        periodic.stop();

        System.exit(0);
    }

    private void initUI() throws InitializationException {       
        // Settings Dialog
        try {
            EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    settingsDialog = new SettingsDialog();
                    
                    for (Profile p : config.getProfiles().list()) {
                        settingsDialog.addProfileToTree(p, false);
                    }
                }
            });
        } 
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to init SettingsDialog.", ex);
            throw new InitializationException(ex);
        } 
        
        // Tray
        tray.init();
        tray.addTrayEventListener(new TrayEventListenerImpl());         
        tray.updateUI();

        // Desktop integration
        if (config.isServiceEnabled()) {
            desktop.start(); // must be started before indexer!
        }        
    }
    
   /* public void activateProfile(Profile profile) throws InitializationException {
  
        // And the rest
        try {
            profile.getRepository().update();
        } 
        catch (CacheException ex) {
            throw new InitializationException("Unable to initialize cache. EXITING.", ex);
        }
        catch (StorageConnectException ex) {
            logger.warning("Could not connect to storage. Profile "+profile.getName()+" stays disabled.");
            return;
        }
        catch (NoRepositoryFoundException ex) {
            logger.severe("Unable to update repository of profile "+profile.getName()+". Not a valid repository.");
            return;
        }
        catch (StorageException ex) {
            logger.log(Level.SEVERE, null, ex);
            return;
        }


        // Locla watcher
        indexer.index(profile);
        localWatcher.watch(profile);
    }*/
       
    private void initProfiles() {
        for (Profile profile : config.getProfiles().list()) {
            if (!profile.isEnabled()) {
                continue;
            }

            profile.setActive(true);
        }
    }

    private void initFirstTimeWizard() {
        Profile profile = WizardDialog.showWizard();

        // Ok clicked
        if (profile != null) {		
            config.getProfiles().add(profile);
            settingsDialog.addProfileToTree(profile, false);
            tray.updateUI();
            
            try {
                config.save();
            }
            catch (ConfigException ex) {
                logger.log(Level.SEVERE, "Could not save profile from first-start wizard. EXITING.", ex);
                throw new RuntimeException("Could not save profile from first-start wizard. EXITING.", ex);
            }

            profile.setActive(true);
        }
    }
    
    private class TrayEventListenerImpl implements TrayEventListener {
        @Override
        public void trayEventOccurred(TrayEvent event) {
            switch (event.getType()) {
            case OPEN_FOLDER:
                File folder = new File((String) event.getArgs().get(0));
                FileUtil.openFile(folder);

                break;

            case PREFERENCES:                        
                settingsDialog.setVisible(true);
                break;

            case DONATE:
                FileUtil.browsePage(Constants.APPLICATION_DONATE_URL);
                break;

            case WEBSITE:
                FileUtil.browsePage(Constants.APPLICATION_URL);
                break;

            case QUIT:
                doShutdown();
                break;

            default:
                logger.warning("Unknown tray event type: "+event);
                // Fressen.
            }
        } 
    } 
    
}
