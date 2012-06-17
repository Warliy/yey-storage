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
package org.syncany.gui.linux;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import org.syncany.exceptions.ConfigException;
import org.syncany.exceptions.InitializationException;
import org.syncany.gui.tray.Tray;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.syncany.Environment;
import org.syncany.config.Config;
import org.syncany.config.Folder;
import org.syncany.config.Folders;
import org.syncany.config.Profile;
import org.syncany.gui.error.ErrorDialog;
import org.syncany.gui.tray.Tray.StatusIcon;
import org.syncany.util.StringUtil;


/**
 *
 * @author Philipp C. Heckel
 */
public class LinuxNativeClient {
    private static final Config config = Config.getInstance();
    private static final Logger logger = Logger.getLogger(LinuxNativeClient.class.getSimpleName());
    private static final Logger serviceLogger = Logger.getLogger("PythonScript");
    private static ResourceBundle resourceBundle;
    
    private static final LinuxNativeClient instance = new LinuxNativeClient();
    /**
     * Send No-operation request every x milliseconds.
     * Must be lower than {@link LinuxNativeService#TIMEOUT_BEFORE_EXIT}.
     */
    private static final int NOP_INTERVAL = 5000;
    private static final int RETRY_COUNT = 3;
    private static final int RETRY_SLEEP = 50;
    
    private boolean initialized;
    private boolean terminated;
    
    private Process serviceProcess;
    private int servicePort;   
    private BufferedReader serviceIn;
        
    private LinuxNativeClient() {
        initialized = false;
        terminated = false;
        resourceBundle = Config.getInstance().getResourceBundle();
    }
    
    public static synchronized LinuxNativeClient getInstance() {
        return instance;
    }

    public synchronized void init() throws InitializationException {
        if (initialized) {
            return;
        }

        startService();
        startNopThread();
        initialized = true;

        // Set first icon
        send(new UpdateStatusIconRequest(StatusIcon.DISCONNECTED));
    }

    public synchronized void destroy() {
        terminated = true;

        if (serviceProcess != null) {
            serviceProcess.destroy();
        }
    }

    public Object send(Request request) {        
        for (int i=1; i<=RETRY_COUNT; i++) {
            try {	    
                Socket socket = connect();                
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Request
                out.print(request+"\n");
                out.flush();

                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Sent request {0}. Waiting for response ...", request);
                }
                
                // Response
                Object response = request.parseResponse(in.readLine());
                
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Received response: {0}", response);
                }
                                
                socket.close();

                return response;
            }
            catch (Exception ex) {
                if (i < RETRY_COUNT) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Could not send request "+request+" to native server. RETRYING ...", ex);
                    }

                    try { Thread.sleep(RETRY_SLEEP); }
                    catch (InterruptedException e2) { }            
                    
                    continue;
                }
            }
        }
        
        // FAILED.
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, "Could not send request {0} to native server. RETRYING FAILED!", request);
        }

        return null;
    }

    private Socket connect() throws IOException {
        Socket connection = new Socket();

        // Re-connect
        for (int i=0; i<RETRY_COUNT; i++) {
            try {
                connection.connect(new InetSocketAddress("localhost", servicePort), 1000);
                
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Connected to native server on port {0}", servicePort);
                }

                return connection;
            }
            catch (IOException e) {
                if (i < RETRY_COUNT) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Cannot connect to native server on port "+servicePort+". RETRYING ...", e);
                    }

                    try { Thread.sleep(RETRY_SLEEP); }
                    catch (InterruptedException e2) { }            
                    
                    continue;
                }
            }
        }

        // FAILED.
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, "Cannot connect to native server. Retrying failed!");
        }

        throw new IOException("Unable to connect to service on port "+servicePort);
    }
    

    
    public static void main(String[] args) throws ConfigException, InitializationException, InterruptedException {
        //for (Entry<Object, Object> entry : System.getProperties().entrySet()) 
          //  System.out.println(entry.getKey() + " = "+entry.getValue());

        try {
            config.load();
            Tray tray = Tray.getInstance();
            LinuxNativeClient.getInstance().init();
            Object send = LinuxNativeClient.getInstance().send(new BrowseFileRequest(BrowseFileRequest.BrowseType.FILES_ONLY));
            
            
            
            
            tray.init();
            tray.notify("Syncany", "Sending my regards", new File("/home/pheckel/Coding/syncany/syncany/res/logo48.png"));
            Thread.sleep(1000);
            tray.setStatusIcon(StatusIcon.UPDATING);
            Thread.sleep(1000);
            tray.setStatusIcon(StatusIcon.UPTODATE);
            Thread.sleep(1000);
            tray.setStatusIcon(StatusIcon.UPDATING);
            Thread.sleep(1000);
            tray.setStatusIcon(StatusIcon.UPTODATE);
            Thread.sleep(1000);
            tray.updateUI();
            
            int i = 1;
            while(true) {
                Thread.sleep(1000);

                tray.setStatusText(resourceBundle.getString("lnc_downloading_files") + " 1/20 ...");
                Thread.sleep(1000);
                tray.setStatusText(resourceBundle.getString("lnc_downloading_files") + " 2/20 ...");
                Thread.sleep(1000);
                tray.setStatusText(resourceBundle.getString("lnc_downloading_files") + " 3/20 ...");
                Thread.sleep(1000);
                tray.setStatusText(resourceBundle.getString("lnc_downloading_files") + " 4/20 ...");

                Thread.sleep(1000);
            
                Profile profile = new Profile();
                profile.setName("Profile "+(i++));
                profile.setFolders(new Folders(profile));
                Folder folder = new Folder(profile);
                folder.setLocalFile(new File("/home"));
                folder.setRemoteId("home");
                profile.getFolders().add(folder);
                config.getProfiles().add(profile);
                tray.updateUI();
            }
            
        }
        finally {
            //NativeClient.getNativeClient().destroy();
        }
	
    }
    
    private void startService() throws InitializationException {
        try {
            // Path to executable (python2 for arch linux bug #793524) and script
            String pythonBinary = (new File("/usr/bin/python2").exists()) ? "/usr/bin/python2" : "/usr/bin/python";            
            String nativeScript = Environment.getInstance().getAppBinDir()+File.separator+"native.py";               
            
            ProcessBuilder builder = new ProcessBuilder(
                pythonBinary, nativeScript, config.getResDir().getAbsolutePath(), "Everything is up-to-date");
            
            builder.redirectErrorStream(true);

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Starting LinuxNativeService : {0}", builder.command());
            }
            
            // Start the server process
            servicePort = 0;
            serviceProcess = builder.start();	            
            serviceIn = new BufferedReader(new InputStreamReader(serviceProcess.getInputStream()));
            
            // Read the port (one-line)
            String line = "NO_LINE";
            List<String> errors = new ArrayList<String>();            

            while (servicePort == 0) {
                try {
                    line = serviceIn.readLine();
                }
                catch (IOException e) {
                    ErrorDialog.showDialog(new Exception("ERROR: Could not read from native script:\n"+StringUtil.getStackTrace(e)));                                                        
                    return;
                }
                
                if (line == null) {
                    break;
                }
                
                // Parse port
                if (line.startsWith("PORT=")) {
                    servicePort = Integer.parseInt(line.substring("PORT=".length()));                        
                    break;
                }
                
                // Add python error/warning
                errors.add(line);
            }

            // Print errors and warnings
            if (serviceLogger.isLoggable(Level.SEVERE)) {                    
                for (String errline : errors) {
                    serviceLogger.log(Level.SEVERE, "Python script error/warning: {0}", errline);
                }                    
            }
            
            if (servicePort == 0) {
                if (errors.isEmpty()) {
                    ErrorDialog.showDialog(new Exception("PYTHON SCRIPT ERROR: Unable to launch script '"+nativeScript+"'. No warnings or errors?!"));                                    
                }                
                else {
                    ErrorDialog.showDialog(new Exception("PYTHON SCRIPT ERROR:\n"+StringUtil.join(errors, "\n")));                                                    
                }

                return;
            }
            
            // Catch and redirect everything coming from the server
            // send it to the logger.
            if (serviceLogger.isLoggable(Level.INFO)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String line;
                            
                            while ((line = serviceIn.readLine()) != null) {
                                serviceLogger.info(line);
                            }
                        }
                        catch (IOException e) {
                            if (serviceLogger.isLoggable(Level.SEVERE)) {
                                serviceLogger.log(Level.SEVERE, "TRAY SERVICE TERMINATED. ");
                            }
                        }
                    }
                }, "TrayServRead").start();
            }

            Thread.sleep(1000); // TODO do we need this?
        }
        catch (Exception e) {
            throw new InitializationException(e);
        }
    }

    private void startNopThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!terminated) {
                    try { Thread.sleep(NOP_INTERVAL); }
                    catch (InterruptedException ex) {}

                    send(new NopRequest());		     
                }
            }
        }, "NativeNOPThread").start();
    }
}
