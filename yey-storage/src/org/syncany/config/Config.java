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
package org.syncany.config;

import java.awt.Image;
import org.syncany.Constants;
import org.syncany.Environment;
import org.syncany.exceptions.ConfigException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.syncany.util.FileUtil;
import org.syncany.util.ImageUtil;
import org.w3c.dom.Document;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Config {   
    // Note: Do NOT add a logger here, as the logger needs the Config instance.
    private static Logger logger = null;
    public enum UserImageType { None, System, Other };
    
    private static final Environment env = Environment.getInstance();
    private static final Config instance = new Config();

    private File configDir;
    private File configFile;

    private Document doc;
    private ConfigNode self;

    // Config values
    private String userName;
    private UserImageType userImageType;
    private String machineName;
    private boolean serviceEnabled;
    private boolean autostart;
    private boolean notificationsEnabled;
    private ResourceBundle resourceBundle;

    private File resDir;

    private Database database;
    private Cache cache;
    private Profiles profiles;

    private Config() {    
        // Note: Do NOT add a logger here, as the logger needs the Config instance.
        
        configDir = null;
        configFile = null;

        userName = null;
        userImageType = UserImageType.System;
        machineName = null;
        serviceEnabled = true;
        resourceBundle = ResourceBundle.getBundle(Constants.RESOURCE_BUNDLE, Constants.DEFAULT_LOCALE);
                
        /*
         * WARNING: Do NOT add 'Config' as a static final if the class  
         *          is created in the Config constructor, Config.getInstance()
         *          will return NULL. 	
         */	
        database = new Database();
        cache = new Cache();
        profiles = new Profiles();        
    }

    public synchronized static Config getInstance() {
        /*
         * WARNING: Do NOT add 'Config' as a static final if the class  
         *          is created in the Config constructor, Config.getInstance()
         *          will return NULL. 	
         */
        if (instance == null) {
            throw new RuntimeException("WARNING: Config instance cannot be null. Fix the dependencies.");
        }
        
        if (logger == null) {
            logger = Logger.getLogger(Config.class.getSimpleName());
        }

        return instance;
    }

    public String getUserName() {
        return (userName != null) ? userName : env.getUserName();
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public UserImageType getUserImageType() {
        return userImageType;
    }

    public void setUserImageType(UserImageType userImageType) {
        this.userImageType = userImageType;
    }

    public Image getUserImage() {
        return getUserImage(userImageType);
    }
    
    public Image getUserImage(UserImageType aUserImageType) {
        File userImageFile = getUserImageFile(aUserImageType);
	
        return ImageUtil.getScaledImage(
            new ImageIcon(userImageFile.getAbsolutePath()).getImage(),
            Constants.PROFILE_IMAGE_MAX_WIDTH, Constants.PROFILE_IMAGE_MAX_HEIGHT);
    }
    
    public File getUserImageFile() {
        return getUserImageFile(userImageType);
    }
    
    public File getUserImageFile(UserImageType aUserImageType) {
        switch (aUserImageType) {
            case None: return new File(getResDir()+File.separator+"logo48.png");
            case Other: return new File(getConfDir()+File.separator+"profile.png");

            case System: 
            default:
                switch (env.getOperatingSystem()){
                    case Linux:
                        return new File(System.getProperty("user.home")+File.separator+".face");
                    case Windows:
                        return new File(getConfDir()+File.separator+"profile.png");
                    default:
                        return null;
                }
        }		
    }
    

    public String getMachineName() {
        return (machineName != null) ? machineName : env.getMachineName();
    }

    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }

    public void setServiceEnabled(boolean serviceEnabled) {
        this.serviceEnabled = serviceEnabled;
    }

    public boolean isServiceEnabled() {
        return serviceEnabled;
    }

    public boolean isAutostart() {
        return autostart;
    }

    public void setAutostart(boolean autostart) {
        this.autostart = autostart;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public File getConfDir() {
        if (configDir == null) {
            throw new RuntimeException("configDir is null. This cannot be!");
        }

        return configDir;
    }

    public File getResDir() {
        return (resDir != null) ? resDir : env.getAppResDir();
    }
    
    public File getResImage(String imageFilename) {
        return new File(getResDir().getAbsoluteFile()+File.separator+imageFilename);
    }    

    public void setResDir(File resDir) {
        this.resDir = resDir;
    }

    public Database getDatabase() {
        return database;
    }

    public Cache getCache() {
        return cache;
    }

    public Profiles getProfiles() {
        return profiles;
    }
    
    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public void load() throws ConfigException {
        load(env.getDefaultUserConfigDir());
    }

    public synchronized void load(File configFolder) throws ConfigException {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Loading configuration from {0}", configFolder);
        }
        
        configDir = configFolder;
        configFile = new File(configDir.getAbsoluteFile()+File.separator+Constants.CONFIG_FILENAME);

        // Default config dir
        if (!configDir.equals(env.getDefaultUserConfigDir())) {
            // Create if it does not exist
            createDirectory(configDir);
        }

        // Not default config folder: Must exist!
        else if (!configDir.equals(env.getDefaultUserConfigDir())) {
            if (!configDir.exists())
            throw new ConfigException("Config folder "+configDir+" does not exist!");
        }

        createDirectory(new File(configDir+File.separator+Constants.CONFIG_CACHE_DIRNAME));
        createDirectory(new File(configDir+File.separator+Constants.CONFIG_DATABASE_DIRNAME));
        createDirectory(new File(configDir+File.separator+Constants.PROFILE_IMAGE_DIRNAME));


        // Config file: copy from res-dir, if non-existant
        if (!configFile.exists()) {
            File defaultConfigFile = new File(env.getAppConfDir()+File.separator+Constants.CONFIG_DEFAULT_FILENAME);		

            try {		    
                FileUtil.copy(defaultConfigFile, configFile);
            }
            catch (IOException e) {
                throw new ConfigException("Could not copy default config file from "+defaultConfigFile+" to "+configFile, e);
            }
        }	

        // Parse and load!
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            doc = dBuilder.parse(configFile);
            self = new ConfigNode(doc.getDocumentElement());

            loadDOM(self);
        }
        catch (Exception e) {
            throw new ConfigException(e);
        }
    }

    /**
     * Saves the configuration to the file it was loaded from, or the default
     * config file if it has not been loaded at all.
     *
     * <p>Note: This does not save the config to the default config file. To do
     * that, call <code>save(env.getDefaultConfigFile());</code>
     * @throws ConfigException
     */
    public void save() throws ConfigException {
        saveDOM(self);

        // Save file
        doc.getDocumentElement().normalize();

        try {
            FileOutputStream out = new FileOutputStream(configFile);
            DOMSource ds = new DOMSource(doc);
            StreamResult sr = new StreamResult(new OutputStreamWriter(out, "utf-8"));

            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute("indent-number", 4);

            Transformer trans = tf.newTransformer();
            trans.setOutputProperty(OutputKeys.INDENT,"yes");
            trans.transform(ds, sr);

            out.close();
        }
        catch (Exception e) {
            throw new ConfigException(e);
        }
    }

    public void createDirectory(File directory) throws ConfigException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ConfigException("Directory '"+directory+"' does not exist and could not be created.");
        }

        else if (!directory.isDirectory() || !directory.canRead() || !directory.canWrite()) {
            throw new ConfigException("Path '"+directory+"' is not a directory or is not read/writable.");
        }
    }

    private void loadDOM(ConfigNode node) throws ConfigException {
        // Flat values
        userName = node.getProperty("username", env.getUserName());	
        machineName = node.getProperty("machinename", env.getMachineName());
        serviceEnabled = node.getBoolean("service-enabled", true);
        autostart = node.getBoolean("autostart", Constants.DEFAULT_AUTOSTART_ENABLED);
        notificationsEnabled = node.getBoolean("notifications", Constants.DEFAULT_NOTIFICATIONS_ENABLED);
         
        if (userName.isEmpty()) {
            userName = env.getUserName();
        }
	
        if (machineName.isEmpty()) {
            machineName = env.getMachineName();
        }
	
        // Resource bundle
        String lang = node.getProperty("lang");
        
        if (lang != null) {
            try {
                System.out.println("lang = "+lang);
                resourceBundle = ResourceBundle.getBundle(Constants.RESOURCE_BUNDLE, new Locale(lang));
                System.out.println(resourceBundle.getString("cp_new_profile_name"));
            }
            catch (MissingResourceException e) {
                System.out.println("COULD NOT LOAD resource bundle for "+lang);
                /* Use default; Loaded in constructor */
            }
        }               
        
        // User image
        String userImageStr = node.getProperty("userimage");

        if ("none".equals(userImageStr)) {
            userImageType = UserImageType.None;
        }

        else if ("other".equals(userImageStr)) {
            userImageType = UserImageType.Other;
        }

        else {
            userImageType = UserImageType.System;
        }
	
        // Directories	
        resDir = node.getFile("resdir", env.getAppResDir());

        // Tests
        if (!resDir.exists() || !resDir.isDirectory() || !resDir.canRead()) {
            throw new ConfigException("Cannot read resource directory '"+resDir+"'.");
        }

        // Complex subvalues    
        database.load(node.findChildByName("database"));
        cache.load(node.findChildByName("cache"));
        profiles.load(node.findOrCreateChildByXpath("profiles", "profiles"));
    }

    private void saveDOM(ConfigNode node) {
        // Flat values
        node.setProperty("username", userName);
        node.setProperty("machinename", machineName);
        node.setProperty("service-enabled", serviceEnabled);
        node.setProperty("autostart", autostart);
        node.setProperty("notifications", notificationsEnabled);
        
        // User Image
        switch (userImageType) {
            case None: node.setProperty("userimage", "none"); break;
            case Other: node.setProperty("userimage", "other"); break;
            case System: default: node.setProperty("userimage", "system"); break;
        }

        // Complex
        // DO NOT SAVE "database"
        cache.save(node.findOrCreateChildByXpath("cache", "cache"));
        profiles.save(node.findOrCreateChildByXpath("profiles", "profiles"));
    }
}
