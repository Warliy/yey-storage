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
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.syncany.config.Repository;
import org.syncany.exceptions.InvalidRepositoryException;
import org.syncany.util.FileUtil;
import org.syncany.util.StringUtil;

/**
 * Represents the remote repository, its options
 * and its root folders.
 *
 * @author Philipp C. Heckel
 */
public class RepositoryFile extends RemoteFile {
    private static final String REPOSITORY_FILE_VERSION = "1";
    public static final String PREFIX = "repository";
    public static final Pattern FILE_PATTERN = Pattern.compile("^"+PREFIX+"-(\\d+)$");
    public static final String FILE_FORMAT = PREFIX+"-%d";

    private Repository repository;
    private Date lastUpdate;
    
    public RepositoryFile(Repository repository, Date lastUpdate) {
        super(String.format(FILE_FORMAT, lastUpdate.getTime()));
        
        this.repository = repository;
        this.lastUpdate = lastUpdate;
    }    
    
    public static RepositoryFile createRepositoryFile(Repository repository, RemoteFile remoteFile) {
        // Check file 
        Matcher m = FILE_PATTERN.matcher(remoteFile.getName());
        
        if (!m.matches()) {
            throw new IllegalArgumentException("Given remote file is not a repository file: "+remoteFile);
        }
        
        return new RepositoryFile(repository, new Date(Long.parseLong(m.group(1))));
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }
        
    public void read(File localRepoFile) throws IOException, InvalidRepositoryException {
        try {
            byte[] encrypted = FileUtil.readFile(localRepoFile);
            byte[] plaintext = repository.getEncryption().decrypt(encrypted);

            Properties p = new Properties();
            p.load(new ByteArrayInputStream(plaintext));

            // Version / Encryption check
            String version = p.getProperty("version");
            
            if (version == null || !REPOSITORY_FILE_VERSION.equals(version)) {
                throw new InvalidRepositoryException("Invalid repository version : "+version);
            }
            
            // Remote IDs
            String remoteIdsStr = p.getProperty("remoteIds");
            
            if (remoteIdsStr != null) {      
                System.out.println("found remote ids = "+remoteIdsStr);
                
                Set<String> remoteIds = new HashSet<String>(Arrays.asList(remoteIdsStr.split(",")));
                
                if (!remoteIds.equals(repository.getAvailableRemoteIds())) {
                    repository.getAvailableRemoteIds().addAll(remoteIds);
                    repository.setChanged(true);
                }                                                
            }
        }
        catch (GeneralSecurityException e) {
            throw new InvalidRepositoryException(e);
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }
    
    public void write(File localRepoFile) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            
            Properties p = new Properties();
            p.setProperty("version", REPOSITORY_FILE_VERSION);
            p.setProperty("remoteIds", StringUtil.join(repository.getAvailableRemoteIds(), ","));
            p.store(bos, "");

            byte[] plainttext = bos.toByteArray();
            byte[] encrypted = repository.getEncryption().encrypt(plainttext);

            FileUtil.writeFile(encrypted, localRepoFile);
            System.out.println(localRepoFile.length());
        }
        catch (Exception e) {
            throw new IOException(e);
        }
        
    }
    
}
