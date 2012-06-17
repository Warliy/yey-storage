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
package org.syncany.watch.remote;

import org.syncany.repository.Update;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * nodeps = (1.1)
 * deps = (1.1, (1.2, 9.1))
 *        (1.2, ())
 *        (9.1, (9.2, 3.5))
 *        (9.2, ())
 *        (3.5, ())
 *        (4.1, (4.2, 5.1))
 *        (5.1, (5.2)) --- 5.2 has two dependencies!! (prevVers + mergedInto)
 *        (9.2, (5.2)) --- 5.2 MERGED INTO 9.2 !!
 *
 * nodeps = (1)
 * deps = (1, 1
 *
 *  A) after adding update-list:
 *  - 1.1 -- 1.2
 *     `---- 9.1 -- 9.2
 *            `---- 3.5
 *  - 4.1 -- 4.2
 *
 *  B) after taking 1.1
 *  - 1.2
 *  - 9.1 -- 9.2
 *     `---- 3.5
 *  - 4.1 -- 4.2
 *
 *  C) 1.1 fails
 *  - 1.1 (delayed 10 sec.) -- 1.2
 *     `---- 9.1 -- 9.2
 *            `---- 3.5
 *  - 4.1 -- 4.2
 *
 * @author Philipp C. Heckel
 */
public class DependencyQueue {

    private List<UpdateRec> nodeps;
    private Map<UpdateRec, List<UpdateRec>> deps;
    /**
     * Cache map: (fileId, (sorted versions))
     */
    private Map<Long, SortedSet<Long>> versions;

    public DependencyQueue() {
        nodeps = new ArrayList<UpdateRec>();
        deps = new HashMap<UpdateRec, List<UpdateRec>>();
        versions = new HashMap<Long, SortedSet<Long>>();
    }

    /*
     * add()
     *   look for parent
     *   A. parent exists:
     *       look for previous version
     *       AA. previous version exists:
     *           append to previous version deps
     *       AB. previous does not exist:
     *           append to parent
     *   B. parent does not exist:
     *      look for previous version
     *      BA. previous version exists:
     *          append to previous version deps
     *      BB. previous version does not exist:
     *          append to nodeps
     * 
     * add(9.2)
     *   A. parent exists (1.1)
     *   AA. previous version exists (9.1)
     *       --> append to previous version deps (9.1)
     * 
     * add(3.5)
     *   A. parent exists (9.1)
     *   AB. previous version does not exist (would be 3.4)
     *       --> append to parent deps (9.1)
     * 
     * add(4.2)
     *   B. parent does not exist (8.1)
     *   BA. previous version exists (4.1)
     *       --> append to previous version deps (4.1)
     * 
     * add(4.1)
     *   B. parent does not exist (8.1)
     *   BB. previous version does not exist (N/A)
     *       --> append to nodeps
     */
    // e.g. add 9.2
    public synchronized void add(Update u) {
        UpdateRec rec = new UpdateRec(u.getFileId(), u.getVersion(), u);

        // Update already there
        if (deps.containsKey(rec)) {
            return;
        }

        // Update maxVersion cache map (if necessary)
        SortedSet<Long> fileVersions = versions.get(rec.getFileId());

        if (fileVersions == null) {
            fileVersions = new TreeSet<Long>();
            fileVersions.add(rec.getVersion());

            versions.put(rec.getFileId(), fileVersions);
        } else {
            fileVersions.add(rec.getVersion());
        }

        // Add it!
        deps.put(rec, new ArrayList<UpdateRec>());

        UpdateRec parentRec = new UpdateRec(u.getParentFileId(), u.getParentFileVersion(), null);
        UpdateRec prevRec = new UpdateRec(u.getFileId(), u.getVersion() - 1, null);

        // NEW SNIP
        UpdateRec mergedIntoRec = new UpdateRec(u.getMergedFileId(), u.getMergedFileVersion(), null);

        int depCount = 0;

        /*if (deps.containsKey(mergedIntoRec)) {
        // Get real record
        for (UpdateRec r : deps.keySet()) {
        if (r.equals(mergedIntoRec)) {
        deps.get(rec).add(r);
        depCount++;
        
        break;
        }
        }
        
        
        
        //deps.get(rec).add(deps.get(mergedIntoRec));
        //deps.get(mergedIntoRec).add(rec);
        
        }*/

        if (deps.containsKey(mergedIntoRec)) {
            //deps.get(rec).add(mergedIntoRec);
            //deps.get(rec).add(deps.get(mergedIntoRec));
            deps.get(mergedIntoRec).add(rec);
            depCount++;
        }

        if (deps.containsKey(parentRec)) {
            deps.get(parentRec).add(rec);
            depCount++;
        }

        if (deps.containsKey(prevRec)) {
            deps.get(prevRec).add(rec);
            depCount++;
        }

        if (depCount == 0) {
            nodeps.add(rec);
        }

        // Find children of "rec" and make them dependent of it
        List<UpdateRec> depRecs = deps.get(rec);

        for (UpdateRec potDepRec : nodeps) {
            // Item in list is child
            if (potDepRec.getUpdate().getParentFileId() == rec.getFileId() && potDepRec.getUpdate().getParentFileVersion() == rec.getVersion()) {
                depRecs.add(potDepRec);
            } // Item in list is next version
            else if (potDepRec.getFileId() == rec.getFileId() && potDepRec.getVersion() - 1 == rec.getVersion()) {
                depRecs.add(potDepRec);
            }

            // TODO merged version test!
        }

        for (UpdateRec depRec : depRecs) {
            nodeps.remove(depRec);
        }
        // </NEW SNIP>


        // A.
    /*if (deps.containsKey(parentRec)) {
        // AA.
        if (deps.containsKey(prevRec)) {
        deps.get(prevRec).add(rec);
        }
        
        // AB.
        else {
        deps.get(parentRec).add(rec);
        }
        }
        
        // B.
        else {
        // BA.
        if (deps.containsKey(prevRec)) {
        deps.get(prevRec).add(rec);
        }
        
        // BB.
        else {
        // Find children of "rec" and make them dependent of it
        List<UpdateRec> depRecs = deps.get(rec);
        
        for (UpdateRec potDepRec : nodeps) {
        // Item in list is child
        if (potDepRec.getUpdate().getParentFileId() == rec.getFileId() && potDepRec.getUpdate().getParentFileVersion() == rec.getVersion())
        depRecs.add(potDepRec);
        
        // Item in list is next version
        else if (potDepRec.getFileId() == rec.getFileId() && potDepRec.getVersion()-1 == rec.getVersion())
        depRecs.add(potDepRec);
        }
        
        for (UpdateRec depRec : depRecs)
        nodeps.remove(depRec);
        
        // Add it
        nodeps.add(rec);
        }
        }*/
    }

    public void addAll(Collection<Update> updates) {
        for (Update update : updates) {
            add(update);
        }
    }

    /**
     * Remove item without dependency, and move children one
     * level up.
     *
     * @return
     */
    public synchronized Update poll() {
        if (nodeps.isEmpty()) {
            return null;
        }

        // Remove it!
        UpdateRec rec = nodeps.remove(0);

        // Move children one level up (to level 0 = no dependency)
        List<UpdateRec> childRecs = deps.remove(rec);

        for (UpdateRec childRec : childRecs) {
            Update childU = childRec.getUpdate();

            // Check parent
            if (childU.getParentFileId() > 0) {
                UpdateRec parentRec = new UpdateRec(childU.getParentFileId(), childU.getParentFileVersion(), null);

                if (deps.containsKey(parentRec)) {
                    continue;
                }
            }

            // Check previous version
            if (childU.getVersion() > 1) {
                UpdateRec prevRec = new UpdateRec(childU.getFileId(), childU.getVersion() - 1, null);

                if (deps.containsKey(prevRec)) {
                    continue;
                }
            }

            // Check merged versions
            // TODO --- PERFORMANCE !!!!!!!!!!!!!!!!
            // TODO --- PERFORMANCE !!!!!!!!!!!!!!!!
            boolean mergeDepExists = false;

            for (Map.Entry<UpdateRec, List<UpdateRec>> m : deps.entrySet()) {
                if (m.getValue().contains(childRec)) {
                    mergeDepExists = true;
                    break;
                }
            }

            if (mergeDepExists) {
                continue;
            }

            // TODO --- PERFORMANCE !!!!!!!!!!!!!!!!
            // TODO --- PERFORMANCE !!!!!!!!!!!!!!!!

            nodeps.add(childRec);
        }

        // Update max version cache map
        SortedSet<Long> fileVersions = versions.get(rec.getFileId());

        if (fileVersions == null) {
            throw new RuntimeException("DependencyQueue: no versions list for file ID " + rec.getFileId());
        }

        fileVersions.remove(new Long(rec.getVersion()));

        if (fileVersions.isEmpty()) {
            versions.remove(new Long(rec.getFileId()));
        }

        // Return
        return rec.getUpdate();
    }

    public synchronized Long getMaxVersion(Long fileId) {
        SortedSet<Long> fileVersions = versions.get(fileId);

        if (fileVersions == null) {
            return null;
        }

        return fileVersions.last();
    }

    public synchronized boolean isEmpty() {
        return nodeps.isEmpty();
    }

    public void printMaps() {
        for (Map.Entry<UpdateRec, List<UpdateRec>> d : deps.entrySet()) {
            System.err.println("deps: " + d.getKey() + " = " + d.getValue());
        }

        for (UpdateRec d : nodeps) {
            System.err.println("nodeps: " + d);
        }

        for (Map.Entry<Long, SortedSet<Long>> d : versions.entrySet()) {
            System.err.println("versions: " + d.getKey() + " = " + d.getValue());
        }
    }

    public static void main(String[] a) {
        Update u = null;

        Update v11 = new Update();
        v11.setFileId(1);
        v11.setVersion(1);
        Update v12 = new Update();
        v12.setFileId(1);
        v12.setVersion(2);

        Update v91 = new Update();
        v91.setFileId(9);
        v91.setVersion(1);
        v91.setParentFileId(1);
        v91.setParentFileVersion(1);
        Update v92 = new Update();
        v92.setFileId(9);
        v92.setVersion(2);
        v92.setParentFileId(1);
        v92.setParentFileVersion(1);

        Update v35 = new Update();
        v35.setFileId(3);
        v35.setVersion(5);
        v35.setParentFileId(9);
        v35.setParentFileVersion(1);

        Update v41 = new Update();
        v41.setFileId(4);
        v41.setVersion(1);
        v41.setParentFileId(8);
        v41.setParentFileVersion(1);
        Update v42 = new Update();
        v42.setFileId(4);
        v42.setVersion(2);
        v42.setParentFileId(8);
        v42.setParentFileVersion(1);

        Update v51 = new Update();
        v51.setFileId(5);
        v51.setVersion(1);
        v51.setParentFileId(4);
        v51.setParentFileVersion(1);
        Update v52 = new Update();
        v52.setFileId(5);
        v52.setVersion(2);
        v52.setParentFileId(4);
        v52.setParentFileVersion(1);
        v52.setMergedFileId(9);
        v52.setMergedFileVersion(2);

        DependencyQueue d = new DependencyQueue();

        System.err.println("add " + v12);
        d.add(v12);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v11);
        d.add(v11);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v91);
        d.add(v91);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v92);
        d.add(v92);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v35);
        d.add(v35);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v41);
        d.add(v41);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v42);
        d.add(v42);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v51);
        d.add(v51);
        d.printMaps();
        System.err.println("-----");
        System.err.println("add " + v52);
        d.add(v52);
        d.printMaps();
        System.err.println("-----");

        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");
        u = d.poll();
        System.err.println("removed " + u);
        d.printMaps();
        System.err.println("-----");

        //System.err.println("add "+v11); d.add(v11); d.printMaps(); System.err.println("-----");
    /*u = d.poll(); System.err.println("removed "+u); d.printMaps(); System.err.println("-----");
        u = d.poll(); System.err.println("removed "+u); d.printMaps(); System.err.println("-----");
        u = d.poll(); System.err.println("removed "+u); d.printMaps(); System.err.println("-----");
        u = d.poll(); System.err.println("removed "+u); d.printMaps(); System.err.println("-----");
        u = d.poll(); System.err.println("removed "+u); d.printMaps(); System.err.println("-----");*/
    }

    private class UpdateRec {

        private long fileId;
        private long version;
        private Update update;

        public UpdateRec(long fileId, long version, Update update) {
            this.fileId = fileId;
            this.version = version;
            this.update = update;
        }

        public long getFileId() {
            return fileId;
        }

        public void setFileId(long fileId) {
            this.fileId = fileId;
        }

        public Update getUpdate() {
            return update;
        }

        public void setUpdate(Update update) {
            this.update = update;
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final UpdateRec other = (UpdateRec) obj;
            if (this.fileId != other.fileId) {
                return false;
            }
            if (this.version != other.version) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (int) (this.fileId ^ (this.fileId >>> 32));
            hash = 97 * hash + (int) (this.version ^ (this.version >>> 32));
            return hash;
        }

        @Override
        public String toString() {
            return fileId + "." + version;
        }
    }
}
