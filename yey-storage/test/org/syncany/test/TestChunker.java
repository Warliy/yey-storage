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
package org.syncany.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Adler32;
import java.util.zip.Checksum;
import org.syncany.index.Chunker;
import org.syncany.index.Chunker.FileChunk;
import org.syncany.util.FileLister;
import org.syncany.util.FileUtil;
import org.syncany.util.RollingChecksum;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class TestChunker {
    public static void rolling(File file) throws FileNotFoundException, IOException {
        long start = System.currentTimeMillis();
        
        RollingChecksum check = new RollingChecksum();

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[512*1024];

        while (fis.read(buffer) > 0) {
            check.check(buffer, 0, buffer.length);
        }

        fis.close();
        
        int v = check.getValue();                
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("rolling = "+v+"; time = "+duration+"ms");
        
    }
    
    public static void adler32(File file) throws FileNotFoundException, IOException {
        long start = System.currentTimeMillis();
        
        Checksum check = new Adler32();

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[512*1024];

        while (fis.read(buffer) > 0) {
            check.update(buffer, 0, buffer.length);
        }

        fis.close();
        
        long v = check.getValue();                
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("adler32 = "+v+"; time = "+duration+"ms");
        
    }    
    
    public static void chunker(File file) throws FileNotFoundException, IOException {
        Chunker chunker = new Chunker();
        
        long start = System.currentTimeMillis();

        Enumeration<FileChunk> chunks = chunker.createChunks(file, 1);
        FileChunk chunk = null;

        while (chunks.hasMoreElements()) {
            chunk = chunks.nextElement();
            //System.out.println("chunk "+chunk.getChecksum() + "; size "+chunk.getContents().length+"; num "+chunk.getNumber());            
            
            Integer count = chunkCounts.get(chunk.getChecksum());
            if (count == null) {
                count = 1;
            }
            
            chunkCounts.put(chunk.getChecksum(), count);
        }

        long v = chunk.getFileChecksum();
        long duration = System.currentTimeMillis() - start;
        System.out.println("chunker = "+v+"; time = "+duration+"ms");

    }        
    
 public static void chunkerNospeed(File file) throws FileNotFoundException, IOException {
     System.out.print(".");
     totalFilesize += file.length();
     Chunker chunker = new Chunker();

        Enumeration<FileChunk> chunks = chunker.createChunks(file, chunkSize);
        FileChunk chunk = null;

        while (chunks.hasMoreElements()) {
            chunk = chunks.nextElement();
            //System.out.println("chunk "+chunk.getChecksum() + "; size "+chunk.getContents().length+"; num "+chunk.getNumber());            
            
            Integer count = chunkCounts.get(chunk.getChecksum());
            if (count == null) {
                count = 1;
            }
            else {
                count++;
            }
            
            chunkCounts.put(chunk.getChecksum(), count);
        }

    }            
    
    private static int chunkSize = 256;
    private static Long totalFilesize = 0L;
    private static Map<Long, Integer> chunkCounts = new HashMap<Long, Integer>();
        
    public static void main(String[] args) throws Exception {
        
        //rolling(file);
        //adler32(file);
        new FileLister(new File("/home/pheckel/Office"), new FileLister.FileListerListener() {

            @Override
            public boolean fileFilter(File file) {
                return true;
            }

            @Override
            public boolean directoryFilter(File directory) {
                return true;
            }

            @Override
            public void proceedFile(File f) {
                if (f == null || f.isDirectory()) return;
                
                try {
                    chunkerNospeed(f);
                } catch (FileNotFoundException ex) {
                    
                } catch (IOException ex) {
                    
                }
            }

            @Override
            public void enterDirectory(File directory) {
                
                    
            }

            @Override
            public void outDirectory(File directory) {
                
            }

            @Override
            public void startProcessing() {
                
            }

            @Override
            public void endOfProcessing() {
                
            }
        }).start();
        
        
        System.out.println("fixed chunk size = "+chunkSize);
        System.out.println("total file size = "+totalFilesize + "; "+FileUtil.formatSize(totalFilesize));
        
        // (chunk häufigkeit, count of count)
        Long dedupdFilesize = 0L;
        Map<Integer, Integer> chunkStats = new TreeMap<Integer, Integer>();
        
        for (Integer thisChunkCount : chunkCounts.values()) {
            Integer totalChunkCount = chunkStats.get(thisChunkCount);
            
            totalChunkCount = (totalChunkCount == null) ? 1 : totalChunkCount + 1;
            chunkStats.put(thisChunkCount, totalChunkCount);                      
        }
        
        for (Map.Entry<Integer, Integer> e : chunkStats.entrySet()) {
            dedupdFilesize += ((long) e.getValue())*chunkSize*1024;
            System.out.println("appear "+e.getKey() + " times in the sample set = "+e.getValue()+" chunk(s)");
        }
            
        System.out.println("deduped total file size = "+dedupdFilesize+"; "+FileUtil.formatSize(dedupdFilesize));
    //    System.out.println("saved = "+dedupdFilesize+"; "+FileUtil.formatSize(dedupdFilesize));
                
      
    }
}
