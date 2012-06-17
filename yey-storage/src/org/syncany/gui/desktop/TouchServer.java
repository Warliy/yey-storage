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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

/**
 *
 * @author Philipp C. Heckel
 */
public class TouchServer extends AbstractServer 
    implements Runnable /* THIS MUST BE HERE. Otherwise the thread won't start! */ {

    public TouchServer() {
        super(32587);
    }

    public void touch(File file) {
        if (workers.isEmpty()) {
            logger.warning("Cannot touch file. No touch workers available.");
            return;
        }

        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "TouchServer: Touch {0}", file);
        }

        synchronized (workers) {
            for (AbstractWorker worker : workers) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "TouchServer: Sending shell touch to client of {0}", worker);
                }
                
                // Touch it, baby!
                ((TouchWorker) worker).touch(file);
            }
        }
    }

    @Override
    protected AbstractWorker createWorker(Socket clientSocket) {
        return new TouchWorker(clientSocket);
    }

    private class TouchWorker extends AbstractWorker {
        private final BlockingQueue<File> queue;

        public TouchWorker(Socket clientSocket) {
            super(clientSocket);
            queue = new LinkedBlockingQueue<File>();
        }
        
        private void touch(File file) {
            queue.add(file);
        }

        @Override
        public void run() {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("TouchWorker: Client connected.");
            }

            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                File touchFile;

                try {
                    while (true) {
                        touchFile = queue.take();

                        if (touchFile == null) {
                            break;
                        }

                        if (logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO, "TouchWorker: Sending touch {0} ...", touchFile);
                        }
                        
                        out.print("shell_touch\n");
                        out.print("path\t"+touchFile.getAbsolutePath()+"\n");
                        out.print("done\n");
                        out.flush();		
                    }
                }
                catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, "TouchWorker got interrupted. TERMINATING.", ex);
                    return;
                }

                out.close();
                in.close();
                clientSocket.close();
            }
            catch (IOException e) {
                logger.log(Level.SEVERE, "Socket error in TouchWorker.", e);
            }
        }
    }
}
