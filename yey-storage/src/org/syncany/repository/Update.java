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
package org.syncany.repository;

import org.syncany.db.CloneFile.Status;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Update {

    private String clientName; // This is just a helper field, NOT saved in the update file!
    private long fileId;
    private long version;
    private String rootId;
    private String parentRootId;
    private long parentFileId;
    private long parentFileVersion;
    private String mergedRootId;
    private long mergedFileId;
    private long mergedFileVersion;
    private Date updated;
    private Status status;
    private Date lastModified;
    private long checksum;
    private long fileSize;
    private boolean folder;
    private String name;
    private String path;
    /**
     * chunkIds (checksums)
     */
    private List<Long> chunksAdded;
    /**
     * count (how many to remove from the end)
     */
    private int chunksRemoved;
    /**
     * (index, chunk-id)
     */
    private Map<Integer, Long> chunksChanged;

    public Update() {
        // Fressen
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public long getChecksum() {
        return checksum;
    }

    public void setChecksum(long checksum) {
        this.checksum = checksum;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getFileId() {
        return fileId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isFolder() {
        return folder;
    }

    public void setFolder(boolean folder) {
        this.folder = folder;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public List<Long> getChunksAdded() {
        return chunksAdded;
    }

    public void setChunksAdded(List<Long> chunksAdded) {
        this.chunksAdded = chunksAdded;
    }

    public Map<Integer, Long> getChunksChanged() {
        return chunksChanged;
    }

    public void setChunksChanged(Map<Integer, Long> chunksChanged) {
        this.chunksChanged = chunksChanged;
    }

    public int getChunksRemoved() {
        return chunksRemoved;
    }

    public void setChunksRemoved(int chunksRemoved) {
        this.chunksRemoved = chunksRemoved;
    }

    public String getRootId() {
        return rootId;
    }

    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    public long getParentFileId() {
        return parentFileId;
    }

    public void setParentFileId(long parentFileId) {
        this.parentFileId = parentFileId;
    }

    public long getParentFileVersion() {
        return parentFileVersion;
    }

    public void setParentFileVersion(long parentFileVersion) {
        this.parentFileVersion = parentFileVersion;
    }

    public String getParentRootId() {
        return parentRootId;
    }

    public void setParentRootId(String parentRootId) {
        this.parentRootId = parentRootId;
    }

    public long getMergedFileId() {
        return mergedFileId;
    }

    public void setMergedFileId(long mergedFileId) {
        this.mergedFileId = mergedFileId;
    }

    public long getMergedFileVersion() {
        return mergedFileVersion;
    }

    public void setMergedFileVersion(long mergedFileVersion) {
        this.mergedFileVersion = mergedFileVersion;
    }

    public String getMergedRootId() {
        return mergedRootId;
    }

    public void setMergedRootId(String mergedRootId) {
        this.mergedRootId = mergedRootId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Update other = (Update) obj;
        if (this.fileId != other.fileId) {
            return false;
        }
        if (this.version != other.version) {
            return false;
        }
        if (this.checksum != other.checksum) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + (int) (this.fileId ^ (this.fileId >>> 32));
        hash = 79 * hash + (int) (this.version ^ (this.version >>> 32));
        hash = 79 * hash + (int) (this.checksum ^ (this.checksum >>> 32));
        return hash;
    }
    
    @Override
    public String toString() {
        return "Update[fileId=" + getFileId() + ", version=" + getVersion() + ", status=" + getStatus() + ", file=" + getPath() + "/" + getName() + "]";
    }
}
