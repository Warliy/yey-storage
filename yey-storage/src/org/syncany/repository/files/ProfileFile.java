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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.syncany.config.Repository;
import org.syncany.exceptions.InvalidRepositoryException;
import org.syncany.util.FileUtil;

/**
 *
 * @author pheckel
 */
public class ProfileFile extends DatedClientRemoteFile {
    public static final String PREFIX = "profile";
    public static final Pattern FILE_PATTERN = Pattern.compile("^"+PREFIX+"-([^-]+)-(\\d+)$");
    public static final String FILE_FORMAT = PREFIX+"-%s-%d";
    
    private String userName;
    
    public ProfileFile(Repository repository, String machineName, Date lastUpdate) {
        super(repository, PREFIX, machineName, lastUpdate);
    }
    
    public static ProfileFile createProfileFile(Repository repository, RemoteFile remoteFile) {
        // Check file 
        Matcher m = FILE_PATTERN.matcher(remoteFile.getName());
        
        if (!m.matches()) {
            throw new IllegalArgumentException("Given remote file is not a profile file: "+remoteFile);
        }
        
        return new ProfileFile(repository, m.group(1), new Date(Long.parseLong(m.group(2))));
    }    

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }            

    @Override
    public void read(File file) throws IOException, InvalidRepositoryException {
        try {
            byte[] encrypted = FileUtil.readFile(file);
            byte[] plaintext = repository.getEncryption().decrypt(encrypted);

            Properties p = new Properties();
            p.load(new ByteArrayInputStream(plaintext));

            setUserName(p.getProperty("name"));
        }
        catch (GeneralSecurityException e) {
            throw new InvalidRepositoryException(e);
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }
    
    public void write(File file) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            
            Properties p = new Properties();
            p.setProperty("name", getUserName());
            p.store(bos, "");

            byte[] plainttext = bos.toByteArray();
            byte[] encrypted = repository.getEncryption().encrypt(plainttext);

            FileUtil.writeFile(encrypted, file);
        }
        catch (Exception e) {
            throw new IOException(e);
        }
        
    }
    
    
}
