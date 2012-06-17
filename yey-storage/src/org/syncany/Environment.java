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

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;

/**
 *
 * @author Philipp C. Heckel
 */
public class Environment {
    private static final Logger logger = Logger.getLogger(Environment.class.getSimpleName());
    private static Environment instance;
    
    public enum OperatingSystem { Windows, Linux, Mac };

    private OperatingSystem operatingSystem;
    private String architecture;
    
    private File defaultUserConfDir;
    private File defaultUserConfigFile;
    
    private File appDir;
    private File appBinDir;
    private File appResDir;
    private File appConfDir;
    private File appLibDir;
    
    /**
     * Local computer name / host name.
     */
    private static String machineName;

    /**
     * Local user name (login-name).
     */
    private static String userName;


    public synchronized static Environment getInstance() {
    if (instance == null)
        instance = new Environment();
	
        return instance;
    }

    private Environment() {
        // Check must-haves
        if (System.getProperty("syncany.home") == null)
            throw new RuntimeException("Property 'syncany.home' must be set.");	

        // Architecture
        if ("32".equals(System.getProperty("sun.arch.data.model"))) {
            architecture = "i386";
        }
        else if ("64".equals(System.getProperty("sun.arch.data.model"))) {
            architecture = "amd64";
        }
        else {
            throw new RuntimeException("Syncany only supports 32bit and 64bit systems, not '"+System.getProperty("sun.arch.data.model")+"'.");	
        }

        // Do initialization!
        if (System.getProperty("os.name").contains("Linux")) {
            operatingSystem = OperatingSystem.Linux;
            defaultUserConfDir = new File(System.getProperty("user.home")+"/."+Constants.APPLICATION_NAME.toLowerCase());	    
        }
        else if (System.getProperty("os.name").contains("Windows")) {
            operatingSystem = OperatingSystem.Windows;
            defaultUserConfDir = new File(System.getProperty("user.home")+"\\"+Constants.APPLICATION_NAME);
        }		 
        else {
            throw new RuntimeException("Your system is not supported at the moment: "+System.getProperty("os.name"));
        }

        // Common values
        defaultUserConfigFile = new File(defaultUserConfDir.getAbsoluteFile()+File.separator+Constants.CONFIG_FILENAME);

        appDir = new File(System.getProperty("syncany.home"));
        appBinDir = new File(appDir.getAbsoluteFile()+File.separator+"bin");
        appResDir = new File(appDir.getAbsoluteFile()+File.separator+"res");
        appConfDir = new File(appDir.getAbsoluteFile()+File.separator+"conf");
        appLibDir = new File(appDir.getAbsoluteFile()+File.separator+"lib");

        // Errors
        if (!appDir.exists() )
            throw new RuntimeException("Could not find application directory at "+appResDir);

        if (!appResDir.exists() )
            throw new RuntimeException("Could not find application resources directory at "+appResDir);

        if (!appConfDir.exists() )
            throw new RuntimeException("Could not find application config directory at "+appConfDir);

        if (!appLibDir.exists() )
            throw new RuntimeException("Could not find application library directory at "+appLibDir);

        // Machine stuff
        try { machineName = InetAddress.getLocalHost().getHostName(); }
        catch (UnknownHostException ex) { machineName = "(unknown)"; }

        userName = System.getProperty("user.name");

        // GUI 
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    //java.util.Enumeration keys = UIManager.getDefaults().keys();
                    
            /*while (keys.hasMoreElements()) {
              Object key = keys.nextElement();
              Object value = UIManager.get (key);
                
              if (value instanceof FontUIResource) {
                  FontUIResource f = (FontUIResource) value;
                  f = new FontUIResource(f.getFamily(), f.getStyle(), f.getSize()-2);
                  System.out.println(key +" = "+value);
                    UIManager.put (key, f);
              
              }
            }*/
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Couldn't set native look and feel.", ex);
        }
    }

    public File getAppConfDir() {
        return appConfDir;
    }

    public File getAppDir() {
        return appDir;
    }

    public File getAppBinDir() {
        return appBinDir;
    }

    public File getAppResDir() {
        return appResDir;
    }

    public File getAppLibDir() {
        return appLibDir;
    }        
    
    public File getDefaultUserConfigFile() {
        return defaultUserConfigFile;
    }

    public File getDefaultUserConfigDir() {
        return defaultUserConfDir;
    }
    
    public String getMachineName() {
        return machineName;
    }

    public String getUserName() {
        return userName;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public String getArchitecture() {
        return architecture;
    }    
    
    public void main(String[] args) {
        Properties properties = System.getProperties();

        Enumeration e = properties.propertyNames();

        System.out.println("Properties");
        System.out.println("---------------");

        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            System.out.println(key+" = "+System.getProperty(key));	    
        }

        System.out.println("ENV");
        System.out.println("---------------");

        for (Map.Entry<String,String> es : System.getenv().entrySet()) {
            System.out.println(es.getKey()+" = "+es.getValue());	
        }
	
    }

}
