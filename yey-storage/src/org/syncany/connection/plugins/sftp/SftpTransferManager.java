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
package org.syncany.connection.plugins.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.syncany.connection.plugins.AbstractTransferManager;
import org.syncany.exceptions.StorageConnectException;
import org.syncany.repository.files.RemoteFile;
import org.syncany.exceptions.StorageException;
import java.io.File; 
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream; 
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Thomas Tschager <dontpanic@tschager.net>
 */
public class SftpTransferManager extends AbstractTransferManager {
    private static final Logger logger = Logger.getLogger(SftpTransferManager.class.getSimpleName());
    private static final int CONNECT_RETRY_COUNT = 3;
    
    private JSch jsch;
    private ChannelSftp sftp;
    private Session session;
    
    public SftpTransferManager(SftpConnection connection) {
        super(connection);
        this.jsch=new JSch();
    } 
 
    @Override
    public SftpConnection getConnection() {
        return (SftpConnection) super.getConnection();
    }

    @Override
    public void connect() throws StorageConnectException {
        for (int i=0; i<CONNECT_RETRY_COUNT; i++) {
            try {
                
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "SFTP client connecting to {0}:{1} ...", new Object[]{getConnection().getHost(), getConnection().getPort()});
                }

                if (getConnection().isKeyAuth()) {
                    
                    jsch.addIdentity(getConnection().getKeyPath(), getConnection().getPassphrase());
                    this.session = jsch.getSession(getConnection().getUsername(), getConnection().getHost(), getConnection().getPort());
                    Hashtable cf = new Hashtable();
                    cf.put("StrictHostKeyChecking", "no");
                    session.setConfig(cf);
                    session.connect();
                    if(!session.isConnected())
                        logger.log(Level.WARNING, "SFTP client: unable to connect (user/password) to {0}:{1} ...", new Object[]{getConnection().getHost(), getConnection().getPort()});

                } else {
                    this.session = jsch.getSession(getConnection().getUsername(), getConnection().getHost(), getConnection().getPort());
                
                    Hashtable cf = new Hashtable();
                    cf.put("StrictHostKeyChecking", "no");
                    session.setConfig(cf);
                    session.setPassword(getConnection().getPassword());
                    session.connect();
                    if(!session.isConnected())
                        logger.log(Level.WARNING, "SFTP client: unable to connect (user/password) to {0}:{1} ...", new Object[]{getConnection().getHost(), getConnection().getPort()});
                }

                this.sftp = (ChannelSftp) session.openChannel("sftp");

                this.sftp.connect();
                if(!sftp.isConnected())
                        logger.log(Level.WARNING, "SFTP client: unable to connect sftp Channel ( {0}:{1} ) ...", new Object[]{getConnection().getHost(), getConnection().getPort()});
                
                return;
            }
            catch (Exception ex) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "SFTP client connection failed.", ex);
                }
                
                throw new StorageConnectException(ex);
            }                        
        }
        
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, "RETRYING FAILED: SFTP client connection failed.");
        }
    }

    @Override
    public void disconnect() {
        this.sftp.disconnect();
        this.session.disconnect();
    }

    @Override
    public void download(RemoteFile remoteFile, File localFile) throws StorageException {
        connect();
        String remotePath = getConnection().getPath()+"/"+remoteFile.getName();

        try {
            // Download file
            File tempFile = config.getCache().createTempFile();
            OutputStream tempFOS = new FileOutputStream(tempFile);

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "SFTP: Downloading {0} to temp file {1}", new Object[]{remotePath, tempFile});
            }

            sftp.get(remotePath, tempFOS);

            tempFOS.close();

            // Move file
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "SFTP: Renaming temp file {0} to file {1}", new Object[]{tempFile, localFile});
            }            
            
            if (!tempFile.renameTo(localFile)) {
                throw new StorageException("Rename to "+localFile.getAbsolutePath()+" failed.");
            }
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Error while downloading file "+remoteFile.getName(), ex);
            throw new StorageException(ex);
        }
    }

    @Override
    public void upload(File localFile, RemoteFile remoteFile) throws StorageException {
        connect();

        String remotePath = getConnection().getPath()+remoteFile.getName();
        String tempRemotePath = getConnection().getPath()+"temp-"+remoteFile.getName();

        try {
            // Upload to temp file
            InputStream fileFIS = new FileInputStream(localFile);

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "SFTP: Uploading {0} to temp file {1}", new Object[]{localFile, tempRemotePath});
            }
       
            sftp.put(fileFIS, tempRemotePath);

            fileFIS.close();

            // Move
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "SFTP: Renaming temp file {0} to file {1}", new Object[]{tempRemotePath, remotePath});
            }    
            
            sftp.rename(tempRemotePath, remotePath);

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not upload file "+localFile+" to "+remoteFile.getName(), ex);
            throw new StorageException(ex);
        }
    }

    @Override
    public Map<String, RemoteFile> list() throws StorageException {
        connect();

        try {
            Map<String, RemoteFile> files = new HashMap<String, RemoteFile>();
            Vector sftpFiles = sftp.ls(getConnection().getPath());

            for (Object file : sftpFiles) {
                if(file instanceof LsEntry){
                    LsEntry f = (LsEntry) file;
                    files.put(f.getFilename(), new RemoteFile(f.getFilename(), f.getAttrs().getSize(), f));
                }
                
            }

            return files;
        }
        catch (SftpException ex) {
            logger.log(Level.SEVERE, "Unable to list SFTP directory.", ex);
            throw new StorageException(ex);
        }
    }

    @Override
    public void delete(RemoteFile remoteFile) throws StorageException {
        connect();

        try {
            sftp.rm(getConnection().getPath() + "/" + remoteFile.getName());
        } 
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not delete file "+remoteFile.getName(), ex);
            throw new StorageException(ex);
        }
    }
}
