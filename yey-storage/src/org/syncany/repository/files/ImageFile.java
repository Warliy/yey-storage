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
package org.syncany.repository.files;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.syncany.config.Repository;

/**
 *
 * @author pheckel
 */
public class ImageFile extends DatedClientRemoteFile {
    public static final String PREFIX = "image";
    public static final Pattern FILE_PATTERN = Pattern.compile("^"+PREFIX+"-([^-]+)-(\\d+)$");
    public static final String FILE_FORMAT = PREFIX+"-%s-%d";   

    public ImageFile(Repository repository, String machineName, Date lastUpdate) {
        super(repository, PREFIX, machineName, lastUpdate);
    }
    
    public static ImageFile createImageFile(Repository repository, RemoteFile remoteFile) {
         // Check file 
        Matcher m = FILE_PATTERN.matcher(remoteFile.getName());
        
        if (!m.matches()) {
            throw new IllegalArgumentException("Given remote file is not a profile file: "+remoteFile);
        }
        
        return new ImageFile(repository, m.group(1), new Date(Long.parseLong(m.group(2))));
    }

    @Override
    public void read(File file) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void write(File file) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
