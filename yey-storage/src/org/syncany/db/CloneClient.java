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

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.syncany.Constants;
import org.syncany.config.Config;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
@Entity
public class CloneClient extends PersistentObject implements Serializable {
    private static final Config config = Config.getInstance();
    private static final long serialVersionUID = 17398420202020111L;

    @Id
    //@GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name="id")
    private Long id;

    @Id
    @Column(name="profile_id", nullable=false)
    private Long profileId;

    @Id
    @Column(name="machinename")
    private String machineName;
    
    @Column(name="username", nullable=true)
    private String userName;    

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="last_update", nullable=true)
    private Date lastUpdate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="last_profile_update", nullable=true)
    private Date lastProfileUpdate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="last_image_update", nullable=true)
    private Date lastImageUpdate;
    
    public CloneClient() {
        // Nothing.
    }

    public CloneClient(String machineName, long profileId) {
        this.machineName = machineName;
        this.id = (long) machineName.hashCode();
        this.profileId = profileId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public String getMachineName() {
        return machineName;
    }

    public void setMachineName(String name) {
        this.machineName = name;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Date getLastImageUpdate() {
        return lastImageUpdate;
    }

    public void setLastImageUpdate(Date lastImageUpdate) {
        this.lastImageUpdate = lastImageUpdate;
    }

    public Date getLastProfileUpdate() {
        return lastProfileUpdate;
    }

    public void setLastProfileUpdate(Date lastProfileUpdate) {
        this.lastProfileUpdate = lastProfileUpdate;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public File getUserImageFile() {
        return new File(config.getConfDir()+File.separator+Constants.PROFILE_IMAGE_DIRNAME+File.separator+getProfileId()+"-"+getMachineName()+".png");	
    }
    
    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof CloneClient)) 
            return false;

        CloneClient other = (CloneClient) object;

        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "CloneClient[id=" + id + ",name="+machineName+"]";
    }

}
