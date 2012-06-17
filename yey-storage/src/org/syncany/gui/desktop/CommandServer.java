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

import org.syncany.db.CloneFile;
import org.syncany.db.DatabaseHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.syncany.config.Config;
import org.syncany.db.CloneFile.Status;
import org.syncany.exceptions.CommandException;
import org.syncany.util.DateUtil;
import org.syncany.util.FileUtil;
import org.syncany.util.StringUtil;

/**
 *
 * @author Philipp C. Heckel
 */
public class CommandServer extends AbstractServer
    implements Runnable /* THIS MUST BE HERE. Otherwise the thread won't start! */ {

    private static final Config config = Config.getInstance();
    private Map<File, Date> queryCache;
    private DatabaseHelper db;

    public CommandServer() {
        super(32586);

        queryCache = new HashMap<File, Date>();
    	db = DatabaseHelper.getInstance();
    }

    @Override
    protected AbstractWorker createWorker(Socket clientSocket) {
        return new CommandWorker(clientSocket);
    }

    public boolean inQueryCache(File file) {
        return queryCache.containsKey(file);
    }

    private class CommandWorker extends AbstractWorker {
        public CommandWorker(Socket clientSocket) {
            super(clientSocket);
        }

        @Override
        public void run() {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("CommandServer: Client connected.");
            }
            
            PrintWriter out;
            BufferedReader in;

            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            }
            catch (IOException ex) {
                logger.log(Level.SEVERE, "Could not create Input/OutputStream for CommandWorker.", ex);
                return;
            }

            try {
                while (true) {
                    String command = in.readLine();

                    if (command == null) {
                        logger.severe("CommandServer (worker "+Thread.currentThread().getName()+"): Client disconnected. EXITING WORKER.");
                        return;
                    }

                    if ("exit".equals(command)) {
                        processExitCommand(out);
                        return;
                    }

                    // Commands
                    try {
                        if (logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO, "Received command ''{0}''.", new Object[]{command});
                        }

                        Map<String, List<String>> args = readArguments(in);

                        if ("get_emblems".equals(command) || "icon_overlay_file_status".equals(command)) {
                            processGetEmblemsCommand(out, command, args);                       
                        }

                        else if ("icon_overlay_context_options".equals(command)) {
                            processContextOptionsCommand(out, command, args);
                        }

                        else if ("get_folder_tag".equals(command)) {
                            processGetFolderTagCommand(out, command, args);
                        }

                        else if ("get_emblem_paths".equals(command)) {
                            processGetEmblemsPathsCommand(out, command, args);
                        }

                        else if ("icon_overlay_context_action".equals(command)) {
                            processContextActionCommand(out, command, args);
                        }	

                        else {
                            throw new CommandException("Unknown command '"+command+"'.");
                        }
                    }
                    catch (CommandException e) {
                        logger.severe("Error in command, sending error 'notok': "+e.getMessage());

                        out.print("notok\n");
                        out.print(e.getMessage()+"\n");
                        out.print("done\n");
                        out.flush();
                    }
                }

            }
            catch (IOException ex) {
                logger.log(Level.SEVERE, "Exception in CommandWorker.", ex);
            }
            finally {
                try {
                    if (out != null) { 
                        out.close();
                    }
                    
                    if (in != null) {
                        in.close();
                    }

                    clientSocket.close();
                }
                catch (IOException ex) {
                    // Fressen
                }
            }
        }
        
        private void processExitCommand(PrintWriter out) {
            out.print("ok\n");
            out.print("Exiting\t\n");
            out.print("done\n");
            out.flush();
        }

        private void processGetEmblemsCommand(PrintWriter out, String command, Map<String, List<String>> args) throws CommandException {
            if (!args.containsKey("path")) {
                throw new CommandException("Invalid Arguments: Argument 'path' missing.");
            }

            File file = new File(args.get("path").get(0));
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Command {0}: path= ''{1}''", new Object[]{command, file.getAbsolutePath()});
            }

            if (!file.exists()) {
                throw new CommandException("Invalid Argument: Given path '"+file.getAbsolutePath()+"' does not exist.");
            }

            boolean embl = "get_emblems".equals(command);
            String resultKey = (embl) ? "emblems" : "status";
            String resultValue = "";
            
            // Find current version in DB
            CloneFile cf = db.getFileOrFolder(file);
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Command '{0}': DB entry for {1}:    {2} (sync: {3})", new Object[]{command, file, cf, (cf != null) ? cf.getSyncStatus() : "N/A"});
            }
            
            if (cf != null) {
                queryCache.put(file, new Date());

                switch (cf.getSyncStatus()) {
                    case CONFLICT: resultValue = (embl) ? "unsyncable" : "unsyncable"; break;
                    case SYNCING:  resultValue = (embl) ? "syncing" : "syncing"; break;
                    case UPTODATE: resultValue = (embl) ? "uptodate" : "up to date"; break;
                    default: resultValue = "";
                }
            }

            // Return result
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Command {0} RESULT: {1}={2} for path= ''{3}''", new Object[]{command, resultKey, resultValue, file.getAbsolutePath()});
            }
            
            out.print("ok\n");
            out.print(resultKey+"	"+resultValue+"\n");
            out.print("done\n");
            out.flush();
        }

        private void processContextOptionsCommand(PrintWriter out, String command, Map<String, List<String>> args) throws CommandException {
            if (!args.containsKey("paths")) {
                throw new CommandException("Invalid Arguments: Argument 'path' missing.");
            }

            String path = args.get("paths").get(0);
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Command {0}: icon_overlay_context_options: paths= ''{1}''", new Object[]{command, path});
            }

            // Find file in DB
            List<String> options = new ArrayList<String>();
            String optionsStr = "";
            CloneFile currentVersion = db.getFileOrFolder(new File(path));

            if (currentVersion != null) {
                int i = 0;				
                List<CloneFile> previousVersions = currentVersion.getPreviousVersions();

                for (CloneFile pv : previousVersions) {
                    if (pv.getStatus() == Status.MERGED) {
                        continue;
                    }

                    // Not more than 10
                    if (i > 10) {
                        break;
                    }

                    i++;
                    options.add(
                        "Restore '"+pv.getName()+"' ("+
                        DateUtil.toNiceFormat(pv.getUpdated())+", "+
                        FileUtil.formatSize(pv.getFileSize())+
                        ")~Restores the file from the remote storage.~restore-"+pv.getFileId()+"-"+pv.getVersion());
                }               
            }
            
            // Concatenate
            options.add("Browse Version History ...~Opens a new dialog and shows the different file versions visually.~versions");
            optionsStr = StringUtil.join(options, "	");            

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Command {0}: Result is: options\t{1}\n", new Object[]{command, optionsStr});
            }
            
            // Return
            out.print("ok\n");				
            out.print("options	"+optionsStr+"\n");
            out.print("done\n");
            out.flush();
        }

        private void processGetFolderTagCommand(PrintWriter out, String command, Map<String, List<String>> args) throws CommandException {
            if (!args.containsKey("path")) {
                throw new CommandException("Invalid Arguments: Argument 'path' missing.");
            }

            String path = args.get("path").get(0);
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Command {0}: get_folder_tag: path= ''{1}''", new Object[]{command, path});    
            }           

            // TODO check status

            out.print("ok\n");
            out.print("tag	\n");
            out.print("done\n");
            out.flush();            
        }

        private void processGetEmblemsPathsCommand(PrintWriter out, String command, Map<String, List<String>> args) {
            /*if (!args.containsKey("path"))
            throw new CommandException("Invalid Arguments: Argument 'path' missing.");

            String path = args.get("path").get(0);
            logger.info("CommandServer (worker "+Thread.currentThread().getName()+"): get_emblem_paths: path= '"+path+"'");
            */

            // TODO check status

            out.print("ok\n");
            out.print("path	"+config.getResDir().getAbsolutePath()+File.separator+"emblems\n");
            out.print("done\n");
            out.flush();            
        }

        private void processContextActionCommand(PrintWriter out, String command, Map<String, List<String>> args) throws CommandException {
            if (!args.containsKey("verb")) {
                throw new CommandException("Invalid Arguments: Argument 'verb' missing.");
            }

            if (!args.containsKey("paths")) {
                throw new CommandException("Invalid Arguments: Argument 'paths' missing.");
            }

            String verb = args.get("verb").get(0);
            String path = args.get("paths").get(0);
            
            // Do it!
            if (verb.startsWith("restore")) {
                logger.log(Level.WARNING, "Command {0}: RESTORE NOT YET IMPLEMENTED. IGNORING.", new Object[]{command, verb});                
            }
                        
            else {
                logger.log(Level.WARNING, "Command {0}: Unknown verb ''{1}''. IGNORING.", new Object[]{command, verb});                
            }                

            out.print("ok\n");
            out.print("done\n");
            out.flush();
            
        }
    }
}
