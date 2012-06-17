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

import com.Ostermiller.util.CSVParser;
import com.Ostermiller.util.CSVPrinter;
import com.Ostermiller.util.LabeledCSVParser;
import org.syncany.db.CloneChunk;
import org.syncany.db.CloneFile;
import org.syncany.db.CloneFile.Status;
import org.syncany.util.StringUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.syncany.config.Repository;
import org.syncany.repository.Update;

/**
 * Represents an update file of one client. File format is CSV.
 * It is a remote file with fixed syntax. Once it's downloaded to a local file
 * it can be read and written.
 * 
 * <pre>
 * fileId,version,versionId,updated,status,lastModified,checksum,fileSize,name,path,chunks
 * 1282850694262,1,12828..,...,NEW,...,-309591037,11,AAA,folder,CHUNKS_SYNTAX
 * 1282850694262,2,12828..,...,RENAMED,...,-309591037,11,AAA-renamed,folder,CHUNKS_SYNTAX
 * ...
 * </pre>
 *
 * <pre>
 * +CHUNK_ID                Add chunk with checksum CHUNK_ID to the end
 * -CHUNKS_COUNT            Remove CHUNKS_COUNT chunks from the end
 * CHUNK_INDEX=CHUNK_ID     ...
 * </pre>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class UpdateFile extends DatedClientRemoteFile {
    public static final String PREFIX = "update";
    public static final Pattern FILE_PATTERN = Pattern.compile("^"+PREFIX+"-([^-]+)-(\\d+)$");
    public static final String FILE_FORMAT = PREFIX+"-%s-%d";     
    
    /*
     * file versions to be saved in the file
     */
    private List<CloneFile> versions;
    
    /**
     * (file, (version, update))
     * the version-map (tree-map) is sorted ascending!
     */
    private Map<Long, TreeMap<Long, Update>> updates;
    
    /**
     * Either this one, or the "updates" list has to go!
     */
    private List<Update> flatUpdateList;

    public UpdateFile(Repository repository, String clientName, Date lastUpdate) {
        super(repository, PREFIX, clientName, lastUpdate);

        this.updates = new HashMap<Long, TreeMap<Long, Update>>();
        this.flatUpdateList = new ArrayList<Update>();
    }
    
    public static UpdateFile createUpdateFile(Repository repository, RemoteFile remoteFile) {
        // Check file 
        Matcher m = FILE_PATTERN.matcher(remoteFile.getName());
        
        if (!m.matches()) {
            throw new IllegalArgumentException("Given remote file is not a profile file: "+remoteFile);
        }
        
        return new UpdateFile(repository, m.group(1), new Date(Long.parseLong(m.group(2))));
    }    

    @Override
    public void read(File file) throws IOException {
        read(file, true);
    }

    public void read(File file, boolean gzipped) throws IOException {
        // TODO encrypt

        LabeledCSVParser csv = (gzipped)
            ? new LabeledCSVParser(new CSVParser(new GZIPInputStream(new FileInputStream(file))))
            : new LabeledCSVParser(new CSVParser(new FileInputStream(file)));

        while (csv.getLine() != null) {
            Update update = new Update();

            update.setRootId(csv.getValueByLabel("rootId"));
            update.setFileId(Long.parseLong(csv.getValueByLabel("fileId")));
            update.setVersion(Long.parseLong(csv.getValueByLabel("version")));
            update.setUpdated(new Date(Long.parseLong(csv.getValueByLabel("updated"))));
            update.setStatus(Status.valueOf(csv.getValueByLabel("status")));
            update.setLastModified(new Date(Long.parseLong(csv.getValueByLabel("lastModified"))));
            update.setChecksum(Long.parseLong(csv.getValueByLabel("checksum")));
            update.setClientName(csv.getValueByLabel("clientName"));
            update.setFileSize(Long.parseLong(csv.getValueByLabel("fileSize")));
            update.setFolder("1".equals(csv.getValueByLabel("folder")));
            update.setName(csv.getValueByLabel("name"));
            update.setPath(csv.getValueByLabel("path"));

            // Parent
            if (csv.getValueByLabel("parentFileId") != null && !csv.getValueByLabel("parentFileId").isEmpty()) {
                update.setParentRootId(csv.getValueByLabel("parentRootId"));
                update.setParentFileId(Long.parseLong(csv.getValueByLabel("parentFileId")));
                update.setParentFileVersion(Long.parseLong(csv.getValueByLabel("parentFileVersion")));
            }

            // Merged Into
            if (csv.getValueByLabel("mergedFileId") != null && !csv.getValueByLabel("mergedFileId").isEmpty()) {
                update.setMergedRootId(csv.getValueByLabel("mergedRootId"));
                update.setMergedFileId(Long.parseLong(csv.getValueByLabel("mergedFileId")));
                update.setMergedFileVersion(Long.parseLong(csv.getValueByLabel("mergedFileVersion")));
            }	    

            // Parse chunks-value
            String[] chunks = csv.getValueByLabel("chunks").split(",");

            List<Long> chunksAdded = new ArrayList<Long>();
            Map<Integer, Long> chunksChanged = new HashMap<Integer, Long>();
            int chunksRemoved = 0;

            // Only do detailed checks if the chunks have changed
            if (!(chunks.length == 1 && chunks[0].isEmpty())) {
            // 1a. First version: "123,124,125,..."
            if (update.getVersion() == 1) {
                for (String chunk : chunks) {
                    if (chunk.isEmpty()) {
                        continue;
                    }

                    chunksAdded.add(Long.parseLong(chunk));
                }
            }

            // 1b. Not the first version: "0=123,+124,+125"
            else {
                for (String chunk : chunks) {
                if (chunk.isEmpty())
                    continue;

                String pos1 = chunk.substring(0,1);

                if ("+".equals(pos1))
                    chunksAdded.add(Long.parseLong(chunk.substring(1,chunk.length())));

                else if ("-".equals(pos1))
                    chunksRemoved = Integer.parseInt(chunk.substring(1,chunk.length()));

                else {
                    String[] changeChunk = chunk.split("=");
                    chunksChanged.put(Integer.parseInt(changeChunk[0]), Long.parseLong(changeChunk[1]));
                }
                }
            }
            }

            update.setChunksAdded(chunksAdded);
            update.setChunksRemoved(chunksRemoved);
            update.setChunksChanged(chunksChanged);


            // Add to map
            TreeMap<Long, Update> fileVersionUpdates = updates.get(update.getFileId());

            if (fileVersionUpdates == null) 
            fileVersionUpdates = new TreeMap<Long, Update>();


            fileVersionUpdates.put(update.getVersion(), update);

            updates.put(update.getFileId(), fileVersionUpdates);
            flatUpdateList.add(update);
        }
    }

    @Override
    public void write(File file) throws IOException {
        write(file, true);
    }

    public void write(File file, boolean gzipped) throws IOException {
        // TODO encrypt

        CSVPrinter csv = (gzipped)
            ? new CSVPrinter(new GZIPOutputStream(new FileOutputStream(file)))
            : new CSVPrinter(new FileWriter(file));

        csv.writeln(new String[]{
            "rootId",
            "fileId",
            "version",
            "parentRootId",
            "parentFileId",
            "parentFileVersion",
            "mergedRootId",
            "mergedFileId",
            "mergedFileVersion",    
            "updated",
            "status",
            "lastModified",
            "checksum",
            "clientName",
            "fileSize",
            "folder",
            "name",
            "path",
            "chunks"
        });

        for (CloneFile cf : versions) {
            // Create 'chunks' string
            List<String> chunksStr = new ArrayList<String>();

            if (cf.getStatus() == Status.RENAMED || cf.getStatus() == Status.DELETED) {
                // Fressen.
            }
            else {
                CloneFile pv = cf.getPreviousVersion();

                // New string (first version): "1,2,3,4,..."
                if (pv == null) {
                    List<CloneChunk> chunks = cf.getChunks();

                    for (CloneChunk chunk : chunks)
                    chunksStr.add(Long.toString(chunk.getChecksum()));
                }

                // Change string (not the first version!): "3=121,+122" or "0=123,-5"
                else {
                    List<CloneChunk> currentChunks = cf.getChunks();
                    List<CloneChunk> previousChunks = pv.getChunks();
                    int minChunkCount = (currentChunks.size() > previousChunks.size()) ? previousChunks.size() : currentChunks.size();

                    //System.err.println("current chunks: "+cf.getChunks());
                    //System.err.println("previo. chunks: "+pv.getChunks());
                    // 1. Change
                    for (int i=0; i<minChunkCount; i++) {
                        // Same chunk in both files; do nothing
                        if (currentChunks.get(i).getChecksum() == previousChunks.get(i).getChecksum()) {
                            continue;
                        }

                        chunksStr.add(i+"="+currentChunks.get(i).getChecksum());
                    }

                    // 2a. The current file has more chunks than the previous one; add the rest
                    if (currentChunks.size() > previousChunks.size()) {
                        for (int i=previousChunks.size(); i<currentChunks.size(); i++) {
                            chunksStr.add("+"+currentChunks.get(i).getChecksum());
                        }
                    }

                    // 2b. The current file has fewer chunks than the previous one; remove the rest
                    else if (currentChunks.size() < previousChunks.size()) {
                        chunksStr.add("-"+(previousChunks.size()-currentChunks.size()));
                    }
                }
            } // create chunks-string

            // Write line
            Long updatedStr = (cf.getUpdated() == null) ? 0L : cf.getUpdated().getTime();
            Long lastModifiedStr = (cf.getLastModified() == null) ? 0L : cf.getLastModified().getTime();

            csv.writeln(new String[] {
                cf.getRootId(),
                Long.toString(cf.getFileId()),
                Long.toString(cf.getVersion()),
                (cf.getParent() != null) ? cf.getParent().getRootId() : "",
                (cf.getParent() != null) ? Long.toString(cf.getParent().getFileId()) : "",
                (cf.getParent() != null) ? Long.toString(cf.getParent().getVersion()) : "",
                (cf.getMergedTo() != null) ? cf.getMergedTo().getRootId() : "",
                (cf.getMergedTo() != null) ? Long.toString(cf.getMergedTo().getFileId()) : "",
                (cf.getMergedTo() != null) ? Long.toString(cf.getMergedTo().getVersion()) : "",	
                Long.toString(updatedStr),
                cf.getStatus().toString(),
                Long.toString(lastModifiedStr),
                Long.toString(cf.getChecksum()),
                cf.getClientName(),
                Long.toString(cf.getFileSize()),
                (cf.isFolder()) ? "1" : "0",
                cf.getName(),
                cf.getPath(),
                StringUtil.join(chunksStr,",")
            });
        }

        csv.close();
    }

    public TreeMap<Long, Update> getFileUpdates(long fileId) {
        return updates.get(fileId);
    }

    public Update getFileUpdate(long fileId, long version) {
        TreeMap<Long, Update> versionUpdates = updates.get(fileId);

        if (versionUpdates == null) {
            return null;
        }

        return versionUpdates.get(version);
    }

    public Set<Long> getFileIds() {
        return updates.keySet();
    }

    public List<Update> getUpdates() {
        return flatUpdateList;
    }

    public void setVersions(List<CloneFile> versions) {
        this.versions = versions;
    }
}
