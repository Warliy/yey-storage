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
package org.syncany.connection.plugins.samba;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import jcifs.smb.SmbException;
import org.syncany.connection.plugins.AbstractTransferManager;
import org.syncany.exceptions.LocalFileNotFoundException;
import org.syncany.exceptions.RemoteFileNotFoundException;
import org.syncany.exceptions.StorageConnectException;
import org.syncany.exceptions.StorageException;
import org.syncany.repository.files.RemoteFile;
import org.syncany.util.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import jcifs.smb.SmbFilenameFilter;

/**
 *
 * @author Philipp C. Heckel
 */
public class SambaTransferManager extends AbstractTransferManager {
    private SmbFile root;
    
    public SambaTransferManager(SambaConnection connection) {
        super(connection);
    root = null;
    }

    @Override
    public SambaConnection getConnection() {
        return (SambaConnection) super.getConnection();
    }

    @Override
    public void connect() throws StorageConnectException {
    if (root != null)
        return;
	
    try {
        root = new SmbFile(getConnection().getRoot());
    }
    catch (MalformedURLException ex) {
        throw new StorageConnectException(ex);
    }
    }

    @Override
    public void disconnect() throws StorageException {
    // Fressen.
    }

    @Override
    public void download(RemoteFile remoteFile, File localFile) throws RemoteFileNotFoundException, StorageException {
    connect();
  
    try {
        SmbFile repoFile = getRepoFile(remoteFile);

        if (!repoFile.exists())
        throw new RemoteFileNotFoundException("No such file in local repository: "+repoFile);
	    
        File tempLocalFile = config.getCache().createTempFile();

        copy(repoFile, tempLocalFile);
        tempLocalFile.renameTo(localFile);
    }
    catch (IOException ex) {
        throw new StorageException("Unable to download file "+remoteFile+" from local repository to "+localFile, ex);
    }
    }

    @Override
    public void upload(File localFile, RemoteFile remoteFile) throws LocalFileNotFoundException, StorageException {
    connect();

    try {
        SmbFile repoFile = getRepoFile(remoteFile);
        SmbFile tempRepoFile = new SmbFile(FileUtil.getAbsoluteParentDirectory(repoFile.getCanonicalPath())+File.separator+".temp-"+repoFile.getName());

        // Do not overwrite files!
        if (repoFile.exists())
        return;

        // No such local file
        if (!localFile.exists())
        throw new LocalFileNotFoundException("No such file on local disk: "+localFile);

        copy(localFile, tempRepoFile);
        tempRepoFile.renameTo(repoFile);
    }
    catch (IOException ex) {
        throw new StorageException("Unable to copy file "+localFile+" to Samba repository", ex);
    }
    }

    @Override
    public void delete(RemoteFile remoteFile) throws RemoteFileNotFoundException, StorageException {
    connect();

    try {
        SmbFile repoFile = getRepoFile(remoteFile);

        if (!repoFile.exists())
        return;

        repoFile.delete();
    }
    catch (Exception e) {
        throw new StorageException(e);
    }
    }

    @Override
    public Map<String, RemoteFile> list() throws StorageException {
        return list(null);
    }

    @Override
    public Map<String, RemoteFile> list(final String namePrefix) throws StorageException {
    connect();
	
    try {
        SmbFile[] files;

        if (namePrefix == null) {
        files = root.listFiles();
        }
        else {
        files = root.listFiles(new SmbFilenameFilter() {
		    @Override public boolean accept(SmbFile dir, String name) {
            return name.startsWith(namePrefix); }
        });
        }

        if (files == null)
        throw new StorageException("Unable to read samba respository "+root);

        Map<String, RemoteFile> remoteFiles = new HashMap<String, RemoteFile>();

        for (SmbFile file : files)
        remoteFiles.put(file.getName(), new RemoteFile(file.getName(), file.length(), file));
	    
        return remoteFiles;
    }
    catch (Exception e) {
        throw new StorageException(e);
    }	
    }

    @Override
    public void clean() throws StorageException {
    throw new UnsupportedOperationException("Not supported yet.");
    }

    private SmbFile getRepoFile(RemoteFile remoteFile) throws MalformedURLException, UnknownHostException {
        return new SmbFile(root, remoteFile.getName());
    }
    
    private void copy(File srcFile, SmbFile destFile) throws IOException, SmbException {
    FileUtil.copy(new FileInputStream(srcFile), new SmbFileOutputStream(destFile));
    }
    
    private void copy(SmbFile srcFile, File destFile) throws IOException, SmbException {
    FileUtil.copy(new SmbFileInputStream(srcFile), new FileOutputStream(destFile));	
    }    
    
  
}
