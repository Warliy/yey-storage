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

import org.syncany.exceptions.CommandException;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Philipp C. Heckel
 */
public abstract class AbstractServer implements Runnable {
    protected static final Logger logger = Logger.getLogger(AbstractServer.class.getSimpleName());

    protected ServerSocket serverSocket;
    protected final List<AbstractWorker> workers;
    protected final int port;
    protected boolean running;

    public AbstractServer(int port) {
        this.workers = new ArrayList<AbstractWorker>();
        this.serverSocket = null;
        this.port = port;
        this.running = false;
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void run() {
        logger.log(Level.INFO, "AbstractServer: Listening at localhost:{0} ...", port);

        try {
            serverSocket = new ServerSocket(port);
        }
        catch (IOException ex) {
            logger.log(Level.WARNING, "AbstractServer: Unable to bind server to port {0}. Address already in use?", port);
            return;
        }

        setRunning(true);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();

                AbstractWorker worker = createWorker(clientSocket);
                workers.add(worker);

                new Thread(worker, "AbstractWorker").start();
            }
            catch (IOException ex) {
                logger.log(Level.SEVERE, "Client disconnected", ex);
            }
        }

        setRunning(false);
    }

    protected Map<String, List<String>> readArguments(BufferedReader in) throws CommandException, IOException {
        Map<String, List<String>> result = new HashMap<String, List<String>>();
        List<String> argLines = new ArrayList<String>();

        // Read everything! The client must talk until 'done'!
        while (true) {
            String line = in.readLine();
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "AbstractServer: Parameter: > {0}", line);
            }

            if (line == null || line.startsWith("done")) {
                break;
            }

            argLines.add(line);
        }

        // Parse
        for (String line : argLines) {
            String[] parts = line.split("\t");

            if (parts.length == 0) {
                continue; // Ignore empty line!
            }

            if (parts.length == 1) {
                throw new CommandException("Invalid arguments: No value given for parameter '"+parts[0]+"'.");
            }

            String key = parts[0];
            List<String> values = new ArrayList<String>();

            for (int i=1; i<parts.length; i++) {
                values.add(parts[i]);
            }

            result.put(key, values);
        }

        return result;
    }
    
    protected abstract AbstractWorker createWorker(Socket clientSocket);
}
