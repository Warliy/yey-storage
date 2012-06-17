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
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;
import org.syncany.util.FileUtil;
import org.syncany.util.RollingChecksum;
import org.syncany.util.StringUtil;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class TTTDChunker {
    private static final Logger logger = Logger.getLogger(TTTDChunker.class.getSimpleName());   

    private Checksum check = new Adler32();
    private MessageDigest digest;
    
    private int Tmin;
    private int Tmax;
    private int D;
    private int Ddash;
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        TTTDChunker c = new TTTDChunker(128*1024, 256*1024, 80, 40);
        
        // A
        Enumeration<FileChunk> chunks = c.createChunks(new File("/home/pheckel/Downloads/TEST/A"));
        FileChunk chunk = null;

        while (chunks.hasMoreElements()) {
            chunk = chunks.nextElement();
            //System.out.println(StringUtil.toHex(chunk.getChecksum()) + " - ");
            //FileUtil.writeFile(chunk.getContents(), new File("/home/pheckel/Downloads/TEST/A-"+chunk.getNumber()));
        }
        
        // B
        chunks = c.createChunks(new File("/home/pheckel/Downloads/TEST/B"));
        chunk = null;

        while (chunks.hasMoreElements()) {
            chunk = chunks.nextElement();
            System.out.println(StringUtil.toHex(chunk.getChecksum()) + " - ");
            FileUtil.writeFile(chunk.getContents(), new File("/home/pheckel/Downloads/TEST/B-"+chunk.getNumber()));
        }        
    }

    public TTTDChunker(int Tmin, int Tmax, int D, int Ddash) {
        this.Tmin = Tmin;
        this.Tmax = Tmax;
        this.D = D;
        this.Ddash = Ddash;
        
        try {
            this.digest = MessageDigest.getInstance("SHA1");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }        
    
    public synchronized byte[] createChecksum(byte[] data) {
        return createChecksum(data, 0, data.length);
    }
    
    public synchronized byte[] createChecksum(byte[] data, int offset, int length) {
        check.reset();
        check.update(data, offset, length);
        check.getValue();
        return new byte[] { 0, 0, 0 };
        /*digest.reset();
        digest.update(data, offset, length);
        return digest.digest();*/
    }    
    
    public synchronized long createChecksum(File file) throws FileNotFoundException {
        Enumeration<FileChunk> chunks = createChunks(file);
        FileChunk chunk = null;
        
        while (chunks.hasMoreElements()) {
            // Do nothing!
        }
        
        return chunk.getFileChecksum();        
    }
    
    /**
     * Chunk size in KB
     */
    public Enumeration<FileChunk> createChunks(File file) throws FileNotFoundException {
        return new ChunkEnumeration(file);
    }

    public class FileChunk {
        private byte[] checksum;
        private byte[] contents;
        private long size;
        private long number;
        private long fileChecksum;
        
        public FileChunk(byte[] checksum, byte[] contents,  long size, long number, long fileChecksum) {
            this.checksum = checksum;
            this.contents = contents;
            this.size = size;
            this.number = number;
            this.fileChecksum = fileChecksum;
        }            
        
        public byte[] getChecksum() {
            return checksum;
        }

        public byte[] getContents() {
            return contents;
        }

        public long getSize() {
            return size;
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
        private boolean closed;
        private long number;
        
        private Checksum check;
        private RollingChecksum rolling;

        
        public ChunkEnumeration(File file) throws FileNotFoundException {
            this.check = new Adler32();
            this.fis = new CheckedInputStream(new FileInputStream(file), check);
            this.closed = false;
            this.number = 0;    
            
            this.rolling = new RollingChecksum();
            this.rolling.reset();
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
            if (closed) {
                return null;
            }
            
            try {
                // TTTD
                // Treat every round as new file       
                int p = 0; // no need for "l"; always zero
                int backupBreak = 0;
                
                int breakpoint = -1;
                
                byte[] c = new byte[1];
                byte[] buffer = new byte[Tmax];
                //ByteBuffer buffer = ByteBuffer.allocate(Tmax);
                         
                int read;
                
                for (; -1 != (read = fis.read(c)); p++) {
                    buffer[p] = c[0];
                    //buffer.put(c);
                    
                    int hash = c[0];  /// WORKS
                    
                    /*if (p == 0) {
                        //rolling.check(c, 0, 1);
                        rolling.check(c, 0, c.length);
                    }
                    else {
                        for (byte b : c) {
                            rolling.roll(b);//c[0])
                        }                        
                    }
                                        
                    int hash = rolling.getValue();  */                                      
                    
                    if (p < Tmin) {
                        // not at minimum size yet
                        continue;
                    }
                    

                    if ((hash % Ddash) == Ddash-1) {      
                        // possible backup break
                        backupBreak = p;     
                    }

                    if ((hash % D) == D-1) {
                        // we found a breakpoint
                        // before the maximum threshold.
                        breakpoint = p;
                        break;
                    }

                    if (p < Tmax){
                        // we have failed to find a breakpoint,
                        // but we are not at the maximum yet
                        continue;
                    }

                    // when  we  reach  here,  we  have
                    // not  found  a  breakpoint  with
                    // the  main  divisor,  and  we  are
                    // at  the  threshold.  If  there
                    // is  a  backup  breakpoint,  use  it.
                    // Otherwise  impose  a  hard  threshold.
                    if (backupBreak != 0) {
                        breakpoint = backupBreak;
                        break;
                    }
                    else {
                        breakpoint = p;
                        break;
                    }
                }
                                    
                // Close if this was the last bytes
                if (c[0] == -1) {
                    fis.close();
                    closed = true;
                }         
                
                // EOF as breakpoint
                if (breakpoint == -1) {
                    breakpoint = p;
                }
                
                //System.out.println("read = "+p);
                System.out.println("breakpoint = "+breakpoint);
                //System.out.println("buffer = "+Arrays.toString(Arrays.copyOf(buffer, p)));
                
                // Create chunk
                //byte[] chunkContents = buffer.array();
                byte[] chunkContents = buffer;//(breakpoint == buffer.length) ? buffer : Arrays.copyOf(buffer, breakpoint);
                byte[] chunkChecksum = createChecksum(chunkContents);
                long chunkNumber = number++;  
                
                return new FileChunk(chunkChecksum, chunkContents, breakpoint, chunkNumber, 0);//check.getValue());
            } 
            catch (IOException ex) {                
                logger.log(Level.SEVERE, "Error while retrieving next chunk.", ex);
                return null;
            }
        }
        
    }
    
    /*

int  p=0,  l=0,backupBreak=0;
for  (;!endOfFile(input);p++){
unsigned  char  c=getNextByte(input);
unsigned  int  hash=updateHash(c);
if  (p  -  l<Tmin){
//not  at  minimum  size  yet
continue;
}
if  ((hash  %  Ddash)==Ddash-1){      
      
     //possible  backup  break
backupBreak=p;     
}
if  ((hash  %  D)  ==  D-1){
//we  found  a  breakpoint
//before  the  maximum  threshold.
addBreakpoint(p);
backupBreak=0;
l=p;
continue;
}
if  (p-l<Tmax){
//we  have  failed  to  find  a  breakpoint,
//but  we  are  not  at  the  maximum  yet
continue;
}
//when  we  reach  here,  we  have
//not  found  a  breakpoint  with
//the  main  divisor,  and  we  are
//at  the  threshold.  If  there
//is  a  backup  breakpoint,  use  it.
//Otherwise  impose  a  hard  threshold.
if  (backupBreak!=0){
addBreakpoint(backupBreak);
l=backupBreak;
backupBreak=0;
}
else{
addBreakpoint(p);
l=p;
backupBreak=0;
}     
     */
}

