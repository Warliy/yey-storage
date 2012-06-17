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
package org.syncany.db;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Represents the chunk of a file.
 * 
 * @author Philipp C. Heckel
 */
@Entity
public class CloneChunk extends PersistentObject implements Serializable {
    private static final long serialVersionUID = 3232299912L;

    @Id
    @Column(name="checksum")
    private long checksum;

    public CloneChunk() {
        // Nothing.
    }

    public CloneChunk(Long checksum) {
        this();
        this.checksum = checksum;
    }

    public long getChecksum() {
        return checksum;
    }

    public void setChecksum(long checksum) {
        this.checksum = checksum;
    }

    public String getFileName() {
        return String.format("chunk-%020d", getChecksum());
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += checksum;
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof CloneChunk)) {
            return false;
        }
        
        CloneChunk other = (CloneChunk) object;

        if (this.checksum != other.checksum) {
            return false;
        }
        
        return true;
    }

    @Override
    public String toString() {
        return "CloneChunk[checksum=" + checksum + "]";
    }
}
