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
package org.syncany.index;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Chunker {
    private static final Logger logger = Logger.getLogger(Chunker.class.getSimpleName());   

    public Chunker() {
        
    }
    
    public synchronized long createChecksum(byte[] data) {
        return createChecksum(data, 0, data.length);
    }
    
    public synchronized long createChecksum(byte[] data, int offset, int length) {
        Checksum check = new Adler32();
        
        check.reset();
        check.update(data, offset, length);
        return check.getValue();
    }    
    
    public synchronized long createChecksum(File file, int chunkSize) throws FileNotFoundException {
        Enumeration<FileChunk> chunks = createChunks(file, chunkSize);
        FileChunk chunk = null;
        
        while (chunks.hasMoreElements()) {            
            chunk = chunks.nextElement(); 
        }
        
        return (chunk == null) ? 0 : chunk.getFileChecksum();        
    }
    
    /**
     * Chunk size in KB
     */
    public Enumeration<FileChunk> createChunks(File file, int chunkSize) throws FileNotFoundException {
        return new ChunkEnumeration(file, chunkSize);
    }

    public class FileChunk {
        private long checksum;
        private byte[] contents;
        private long number;
        private long fileChecksum;

        public FileChunk(long checksum, byte[] contents, long number) {
            this(checksum, contents, number, 0);
        }  
        
        public FileChunk(long checksum, byte[] contents, long number, long fileChecksum) {
            this.checksum = checksum;
            this.contents = contents;
            this.number = number;
            this.fileChecksum = fileChecksum;
        }            
        
        public long getChecksum() {
            return checksum;
        }

        public byte[] getContents() {
            return contents;
        }

        public long getNumber() {
            return number;
        }

        public long getFileChecksum() {
            return fileChecksum;
        }
    }
    
    public class ChunkEnumeration implements Enumeration<FileChunk> {
        private CheckedInputStream fis;           
        private byte[] buffer;
        private boolean closed;
        private long number;
        
        private Checksum check;
        
        public ChunkEnumeration(File file, int chunkSize) throws FileNotFoundException {
            this.check = new Adler32();
            this.fis = new CheckedInputStream(new FileInputStream(file), check);
            this.buffer = new byte[chunkSize*1024];
            this.closed = false;
            this.number = 0;
        }
        
        @Override
        public boolean hasMoreElements() {
            if (closed) {
                return false;
            }
            
            try {
                //System.out.println("fis ="+fis.available());
                return fis.available() > 0;
            }
            catch (IOException ex) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Error while reading from file input stream.", ex);
                }
                
                return false;
            }
        }

        @Override
        public FileChunk nextElement() {
            try {
                int read = fis.read(buffer);
                
                if (read == -1) {
                    return null;
                }
                
                // Close if this was the last bytes
                if (fis.available() == 0) {
                    fis.close();
                    closed = true;
                }
                
                // Create chunk
                long chunkChecksum = createChecksum(buffer, 0, read);
                byte[] chunkContents = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
                long chunkNumber = number++;  

                return new FileChunk(chunkChecksum, chunkContents, chunkNumber, check.getValue());
            } 
            catch (IOException ex) {                
                logger.log(Level.SEVERE, "Error while retrieving next chunk.", ex);
                return null;
            }
        }
        
    }
}

