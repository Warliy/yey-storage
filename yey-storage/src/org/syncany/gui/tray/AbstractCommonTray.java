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

import org.syncany.config.Folder;
import org.syncany.config.Profile;
import org.syncany.exceptions.InitializationException;
import org.syncany.gui.tray.TrayEvent.EventType;
import java.awt.AWTException;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import javax.swing.UIManager;
import org.syncany.Constants;
import org.syncany.config.Config;

/**
 *
 * @author Philipp C. Heckel
 */
public abstract class AbstractCommonTray extends Tray {

    private SystemTray tray;
    private PopupMenu menu;
    private TrayIcon icon;
    private MenuItem itemStatus;
    private MenuItem itemPreferences;
    private MenuItem itemDonate;
    private MenuItem itemQuit;
    
    private TrayIconStatus status;

    public AbstractCommonTray() {
        super();

        // cp. init
        this.menu = null;
        this.status = new TrayIconStatus(new TrayIconStatus.TrayIconStatusListener() {
            @Override
            public void trayIconUpdated(String filename) {
                if (Config.getInstance() != null) {
                    setIcon(new File(Config.getInstance().getResDir()+File.separator+
                            Constants.TRAY_DIRNAME+File.separator+filename));
                }
            }
        });
    }

    @Override
    public void init() throws InitializationException {
        initMenu();
        initIcon();
    }

    @Override
    public void destroy() {
        // Nothing.
    }

    @Override
    public void setStatusText(String msg) {
        synchronized (itemStatus) {
            itemStatus.setLabel(msg);
        }
    }    
    
    @Override
    public synchronized StatusIcon setStatusIcon(StatusIcon s) {
        return status.setIcon(s);
    }

    @Override
    public StatusIcon getStatusIcon() {
        return status.getIcon();
    }
    
    private void setIcon(File file) {
        icon.setImage(Toolkit.getDefaultToolkit().getImage(file.getAbsolutePath()));        
    }

    @Override
    public void updateUI() {
        initMenu();
    }

    private void initMenu() {
        // Create
        menu = new PopupMenu();

        // Status
        itemStatus = new MenuItem("Everything is up to date");
        itemStatus.setEnabled(false);

        menu.add(itemStatus);

        // Profiles and folders
        List<Profile> profiles = config.getProfiles().list();

        menu.addSeparator();

        if (profiles.size() == 1) {
            Profile profile = profiles.get(0);

            for (final Folder folder : profile.getFolders().list()) {
                if (!folder.isActive() || folder.getLocalFile() == null) {
                    continue;
                }
                
                MenuItem itemFolder = new MenuItem(folder.getLocalFile().getName());

                itemFolder.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        fireTrayEvent(new TrayEvent(EventType.OPEN_FOLDER, folder.getLocalFile().getAbsolutePath()));
                    }
                });

                menu.add(itemFolder);
            }

            menu.addSeparator();
        }
        else if (profiles.size() > 1) {
            for (Profile profile : profiles) {
                Menu itemProfile = new Menu(profile.getName());

                for (final Folder folder : profile.getFolders().list()) {
                    if (!folder.isActive() || folder.getLocalFile() == null) {
                        continue;
                    }
                    
                    MenuItem itemFolder = new MenuItem(folder.getLocalFile().getName());

                    itemFolder.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            fireTrayEvent(new TrayEvent(EventType.OPEN_FOLDER, folder.getLocalFile().getAbsolutePath()));
                        }
                    });

                    itemProfile.add(itemFolder);
                }

                menu.add(itemProfile);
            }

            menu.addSeparator();
        }

        // Preferences
        itemPreferences = new MenuItem("Preferences ...");
        itemPreferences.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                fireTrayEvent(new TrayEvent(EventType.PREFERENCES));
            }
        });

        menu.add(itemPreferences);

        // Donate!
        itemDonate = new MenuItem("Donate ...");
        itemDonate.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                fireTrayEvent(new TrayEvent(EventType.DONATE));
            }
        });

        menu.add(itemDonate);

        // Quit
        menu.addSeparator();

        itemQuit = new MenuItem("Quit");
        itemQuit.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                fireTrayEvent(new TrayEvent(EventType.QUIT));
            }
        });

        menu.add(itemQuit);
    }

    private void initIcon() throws InitializationException {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            throw new InitializationException("Unable to set look and feel for tray icon", e);
        }

        tray = SystemTray.getSystemTray();
        
        File defaultIconFile = new File(Config.getInstance().getResDir()+File.separator+
                Constants.TRAY_DIRNAME+File.separator+Constants.TRAY_FILENAME_DEFAULT);

        Image image = Toolkit.getDefaultToolkit().getImage(defaultIconFile.getAbsolutePath());

        icon = new TrayIcon(image, "syncany", menu);

        icon.setImageAutoSize(true);
        icon.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // popup
            }
        });
        //icon.addMouseListener(mouseListener);

        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new InitializationException("Unable to add tray icon.", e);
        }
    }
}
