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

import org.syncany.gui.error.ErrorDialog;
import org.syncany.config.Config;
import org.syncany.exceptions.ConfigException;
import org.syncany.exceptions.InitializationException;
import java.io.File;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.syncany.util.StringUtil;


/**
 * Main class for the Syncany client.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Syncany {
    private static final Config config = Config.getInstance();
    private static String[] args;

    /**
     * @param args Command line arguments for the Syncany client
     *             See '--help'
     */
    public static void main(String[] args) throws ConfigException, InitializationException {
        Syncany.args = args; // Required for restart
        Syncany.start();
    }

    public static void start() {
        try {
            // create the Options
            Options options = new Options();

            options.addOption("c", "config", true, "Alternative config file (Default: ~/.syncany/config.xml)" );
            options.addOption("h", "help", false, "Print this message.");

            // create the command line parser
            CommandLineParser parser = new PosixParser();
            CommandLine line = parser.parse(options, args);

            // Help
            if (line.hasOption("help")) {
                new HelpFormatter().printHelp("syncany", options );
                System.exit(0);
            }

            // Load config
            if (line.hasOption("config")) {
                config.load(new File(line.getOptionValue("config")));
            }

            else {
                config.load();
            }
        }
        catch (ConfigException e) {
            System.err.println("ERROR: Configuration exception: "+e.getMessage());
            System.err.println(StringUtil.getStackTrace(e));
            System.exit(1);
        }
        catch (ParseException e) {
            System.err.println("ERROR: Command line arguments invalid: "+e.getMessage());
            System.err.println(StringUtil.getStackTrace(e));
            System.exit(1);
        }

        // Start app!
        try {
            new Application().start();
        }
        catch (final Exception e) {
            ErrorDialog.showDialog(e);
        }
    }

}
