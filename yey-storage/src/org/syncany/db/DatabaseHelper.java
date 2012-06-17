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

import org.syncany.repository.Update;
import org.syncany.config.Config;
import org.syncany.Constants;
import org.syncany.config.Folder;
import org.syncany.config.Profile;
import org.syncany.db.CloneFile.Status;
import org.syncany.util.FileUtil;
import org.syncany.util.StringUtil;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import org.syncany.db.CloneFile.SyncStatus;

/**
 * Provides access to the database. 
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DatabaseHelper {

    private static final Config config = Config.getInstance();
    private static final Logger logger = Logger.getLogger(DatabaseHelper.class.getSimpleName());
    private static final DatabaseHelper instance = new DatabaseHelper();

    private DatabaseHelper() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Creating DB helper ...");
        }

        // Nothing.
    }

    public static DatabaseHelper getInstance() {
        return instance;
    }

    public CloneFile getFolder(Folder root, File file) {
        return getFileOrFolder(root, file, true);
    }

    public CloneFile getFile(Folder root, File file) {
        return getFileOrFolder(root, file, false);
    }

    public CloneFile getFileOrFolder(Folder root, File file) {
        return getFileOrFolder(root, file, null);
    }

    public CloneFile getFileOrFolder(File file) {
        // Get root
        Folder root = null;

        // TODO This is terribly implemented, and veeery inefficient!
        a:
        for (Profile profile : config.getProfiles().list()) {
            for (Folder aRoot : profile.getFolders().list()) {
                if (aRoot.getLocalFile() == null) {
                    continue;
                }
                
                if (!file.getAbsolutePath().startsWith(aRoot.getLocalFile().getAbsolutePath())) {
                    continue;
                }

                root = aRoot;
                break a;
            }
        }

        if (root == null) {
            return null;
        }

        //System.err.println("ROOT: "+root.getFile());
        return getFileOrFolder(root, file);
    }

    private CloneFile getFileOrFolder(Folder root, File file, Boolean folder) {
        assert root != null;
        
        // First, check by full file path
        String queryStr =
                "select f from CloneFile f "
                + "where "
                + "      f.rootId = :rootId "
                + ((root != null) ? " and f.profileId = :profileId " : "")
                + "      and f.path = :path "
                + "      and f.name = :name "
                + ((folder != null) ? "and f.folder = :folder " : " ")
                + "      and f.status <> :notStatus1 "
                + "      and f.status <> :notStatus2 "
                //+ "      and f.syncStatus <> :notSyncStatus "
                + "      and f.version = (select max(ff.version) from CloneFile ff where " + ((root != null) ? " ff.profileId = :profileId and " : "") + " ff.rootId = :rootId and f.fileId = ff.fileId) "
                + "      order by f.updated desc";

        Query query = config.getDatabase().getEntityManager().createQuery(queryStr);

        //System.err.println(queryStr);
        //logger.severe("getFileOrFolder: rel parent = " + FileUtil.getRelativeParentDirectory(root.getLocalFile(), file) + " / file name = " + file.getName() + " / folder = " + file.isDirectory());
        query.setMaxResults(1);
        query.setParameter("rootId", root.getRemoteId());
        query.setParameter("path", FileUtil.getRelativeParentDirectory(root.getLocalFile(), file));
        query.setParameter("name", file.getName());
        query.setParameter("notStatus1", Status.DELETED);
        query.setParameter("notStatus2", Status.MERGED);
        //query.setParameter("notSyncStatus", CloneFile.SyncStatus.SYNCING); // this is required for chmgr.applyNewOrChange()

        if (root != null) {
            query.setParameter("profileId", root.getProfile().getId());
        }

        if (folder != null) {
            query.setParameter("folder", folder);
        }

        List<CloneFile> dbFiles = query.getResultList();

        // Error: No matching DB entries found.
        if (dbFiles.isEmpty()) {
            return null;
        }

        // Success
        CloneFile cf = dbFiles.get(0);

        config.getDatabase().getEntityManager().refresh(cf);

        return cf;
    }

    /*
     * get direct children
     */
    public List<CloneFile> getChildren(CloneFile parentFile) {
        // First, check by full file path
        Query query = config.getDatabase().getEntityManager().createQuery(
                "select f from CloneFile f "
                + "where "
                + "          f.profileId = :profileId "
                + "      and f.rootId = :rootId "
                + "      and f.status <> :notStatus1 "
                + "      and f.status <> :notStatus2 "
                + "      and f.version = (select max(ff.version) from CloneFile ff where ff.profileId = :profileId and ff.rootId = :rootId and f.fileId = ff.fileId) "
                + "      and f.parent = :parent");

//	Systconfig.getDatabase().getEntityManager().err.println("rel parent = "+FileUtil.getRelativeParentDirectory(root.getFile(), file) + " / file name = "+file.getName() + " / folder = "+file.isDirectory());
        query.setParameter("profileId", parentFile.getProfile().getId());
        query.setParameter("rootId", parentFile.getRoot().getRemoteId());
        query.setParameter("notStatus1", Status.DELETED);
        query.setParameter("notStatus2", Status.MERGED);
        query.setParameter("parent", parentFile);

        List<CloneFile> children = query.getResultList();

        return children;
    }

    public List<CloneFile> getAllChildren(CloneFile parentFile) {
        // First, check by full file path
        Query query = config.getDatabase().getEntityManager().createQuery(
                "select f from CloneFile f "
                + "where "
                + "          f.profileId = :profileId "
                + "      and f.rootId = :rootId "
                + "      and f.status <> :notStatus1 "
                + "      and f.status <> :notStatus2 "
                + "      and f.version = (select max(ff.version) from CloneFile ff where ff.profileId = :profileId and ff.rootId = :rootId and f.fileId = ff.fileId) "
                + "      and f.path like :pathPrefix");

//	Systconfig.getDatabase().getEntityManager().err.println("rel parent = "+FileUtil.getRelativeParentDirectory(root.getFile(), file) + " / file name = "+file.getName() + " / folder = "+file.isDirectory());
        query.setParameter("profileId", parentFile.getProfile().getId());
        query.setParameter("rootId", parentFile.getRoot().getRemoteId());
        query.setParameter("notStatus1", Status.DELETED);
        query.setParameter("notStatus2", Status.MERGED);
        query.setParameter("pathPrefix", FileUtil.getRelativePath(parentFile.getRoot().getLocalFile(), parentFile.getFile()));

        List<CloneFile> children = query.getResultList();

        return children;
    }

    /**
     * Get file in exact version.
     *
     * @param fileId
     * @param version
     * @return
     */
    public CloneFile getFileOrFolder(Profile profile, long fileId, long version) {
        Query query = config.getDatabase().getEntityManager().createQuery(
                "select f from CloneFile f "
                + "where f.profileId = :profileId "
                + "      and f.fileId = :fileId "
                + "      and f.version = :version");

        query.setParameter("profileId", profile.getId());
        query.setParameter("fileId", fileId);
        query.setParameter("version", version);

        try {
            return (CloneFile) query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Get file in current (newest) version.
     */
    public CloneFile getFileOrFolder(Profile profile, long fileId) {
        Query query = config.getDatabase().getEntityManager().createQuery(
                "select f from CloneFile f "
                + "where f.profileId = :profileId "
                + "      and f.fileId = :fileId "
                + "      and f.version = (select max(ff.version) from CloneFile ff where ff.profileId = :profileId and f.fileId = ff.fileId)");

        query.setParameter("profileId", profile.getId());
        query.setParameter("fileId", fileId);

        try {
            return (CloneFile) query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Check the files with the same checksum and don't exist anymore
     * to determine the 'previous version' of this file.
     *
     * If more file are found, i.e. files with the same checksum that don't
     * exist, choose the one with the smallest Levenshtein distance.
     */
    public CloneFile getNearestFile(Folder root, File file, long checksum) {
        Query query = config.getDatabase().getEntityManager().createQuery(
                "select f from CloneFile f "
                + "where f.profileId = :profileId "
                + "      and f.rootId = :rootId "
                + "      and f.version = (select max(ff.version) from CloneFile ff where f.fileId = ff.fileId) "
                + "      and f.checksum = :checksum "
                + "      and f.status <> :notStatus1 "
                + "      and f.status <> :notStatus2 "
                + "      order by f.updated desc");

        query.setParameter("profileId", root.getProfile().getId());
        query.setParameter("rootId", root.getRemoteId());
        query.setParameter("notStatus1", Status.DELETED);
        query.setParameter("notStatus2", Status.MERGED);
        query.setParameter("checksum", checksum);

        List<CloneFile> sameChecksumFiles = query.getResultList();

        CloneFile nearestPreviousVersion = null;
        int previousVersionDistance = Integer.MAX_VALUE;

        for (CloneFile cf : sameChecksumFiles) {
            // Ignore if the file actually exists
            if (cf.getFile().exists()) {
                continue;
            }

            // Check Levenshtein distance
            int distance = StringUtil.computeLevenshteinDistance(file.getAbsolutePath(), cf.getAbsolutePath());

            if (distance < previousVersionDistance) {
                nearestPreviousVersion = cf;
                previousVersionDistance = distance;
            }
        }

        // No history if the distance exceeds the maximum
        if (previousVersionDistance > Constants.MAXIMUM_FILENAME_LEVENSHTEIN_DISTANCE) {
            nearestPreviousVersion = null;
        }

        return nearestPreviousVersion;
    }
    

    public List<CloneFile> getFiles(Folder root) {
        Query query = config.getDatabase().getEntityManager().createQuery(
                "select f from CloneFile f "
                + "where "
                + "          f.profileId = :profileId "
                + "      and f.rootId = :rootId "
                + "      and f.status <> :notStatus1 "
                + "      and f.status <> :notStatus2 "
                + "      and f.version = (select max(ff.version) from CloneFile ff where ff.profileId = :profileId and ff.rootId = :rootId and f.fileId = ff.fileId) "
        );
        
        query.setParameter("profileId", root.getProfile().getId());
        query.setParameter("rootId", root.getRemoteId());
        query.setParameter("notStatus1", Status.DELETED);
        query.setParameter("notStatus2", Status.MERGED);

        List<CloneFile> children = query.getResultList();

        return children;
        
    }    

    public CloneFile createFile(Profile profile, Update update) {
        return createFile(profile, update, SyncStatus.SYNCING);
    }

    public CloneFile createFile(Profile profile, Update update, SyncStatus syncStatus) {
        EntityManager em = config.getDatabase().getEntityManager();

        CloneFile newFile = new CloneFile();

        newFile.setFileId(update.getFileId());
        newFile.setVersion(update.getVersion());
        newFile.setUpdated(update.getUpdated());
        newFile.setChecksum(update.getChecksum());
        newFile.setProfile(profile);
        newFile.setRootId(update.getRootId());
        newFile.setPath(update.getPath());
        newFile.setName(update.getName());
        newFile.setLastModified(update.getLastModified());
        newFile.setClientName(update.getClientName());
        newFile.setFileSize(update.getFileSize());
        newFile.setStatus(update.getStatus());
        newFile.setSyncStatus(syncStatus);
        newFile.setFolder(update.isFolder());

        if (update.getMergedFileId() != 0) {
            CloneFile mergedVersion = getFileOrFolder(profile, update.getMergedFileId(), update.getMergedFileVersion());
            newFile.setMergedTo(mergedVersion);
        }
        
        if (update.getParentFileId() != 0) {
            CloneFile parentCF = getFileOrFolder(profile, update.getParentFileId(), update.getParentFileVersion());
            newFile.setParent(parentCF);
        }

        // Chunks from previous version
        if (update.getVersion() > 1) {
            CloneFile previousVersion = getFileOrFolder(profile, update.getFileId(), update.getVersion() - 1);
            
            if (previousVersion != null) {            
                newFile.setChunks(previousVersion.getChunks());
            }
            else {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Could not find previous version for file {0} in database.", newFile);
                }
            }           
        }

        // Add Chunks (if there are any!)
        // Triggered for new files (= version 1) AND for grown files (= more chunks)
        if (!update.getChunksAdded().isEmpty()) {
            for (long chunkId : update.getChunksAdded()) {
                CloneChunk chunk = getChunk(chunkId, true);
                newFile.addChunk(chunk);
            }
        }
                
                // Chunks removed
        else if (update.getChunksRemoved() > 0) {
            newFile.removeChunks(update.getChunksRemoved() - 1);
        }

        // Chunks changed
        if (!update.getChunksChanged().isEmpty()) {
            for (Map.Entry<Integer, Long> entry : update.getChunksChanged().entrySet()) {
                int chunkIndex = entry.getKey();
                long chunkId = entry.getValue();

                em.getTransaction().begin();

                CloneChunk chunk = getChunk(chunkId, true);

                em.merge(chunk);
                em.flush();
                em.getTransaction().commit();

                newFile.setChunk(chunkIndex, chunk);
            }
        }

        newFile.merge();

        return newFile;
    }

    /**
     *
     * @param name
     * @return
     */
    /*    public CloneClient getClient(Profile profile, String name) {
    return getClient(profile, name, false);
    }*/
    /**
     * Retrieves the client with the given name from the database. If it does not
     * exist and the {@code create}-parameter is true, it creates a new one and returns
     * it.
     *
     * @param machineName
     * @param create
     * @return Returns the client or null if it does not exist
     */
    public synchronized CloneClient getClient(Profile profile, String machineName, boolean create) {
        for (int i = 1; i < 3; i++) {
            try {
                Query q = config.getDatabase().getEntityManager().createQuery(
                        "select c from CloneClient c where c.profileId = :profileId and c.machineName = :machineName");
                q.setParameter("profileId", profile.getId());
                q.setParameter("machineName", machineName);

                try {
                    return (CloneClient) q.getSingleResult();
                } catch (NoResultException ex) {
                    CloneClient client = null;

                    if (create) {
                        logger.info("Logger: Client '" + machineName + "' unknown. Adding to DB.");
                        
                        client = new CloneClient(machineName, profile.getId());
                        client.merge();
                    }

                    return client;
                }
            } catch (Exception e) {
                logger.warning("Logger: Adding client '" + machineName + "' failed. Retrying (try = " + i + ")");

                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) { /* Fressen */ }

                continue;
            }
        }

        logger.severe("Logger: Adding client '" + machineName + "' FAILED completely. Retrying FAILED.");
        return null;
    }

    /**
     * Retrieves the last chunk/file update.
     */
    public Long getFileVersionCount() {
        // Newest file update
        Query q = config.getDatabase().getEntityManager().createQuery(
                "select count(f.fileId) from CloneFile f");

        q.setMaxResults(1);
        //q.setParameter("accountId", account.getId());

        try {
            return (Long) q.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    public CloneChunk getChunk(long checksum, boolean create) {
        CloneChunk chunk = null;

        int tryCount = 1;
        int maxTries = 5;

        while (tryCount++ <= maxTries) {
            Query query = config.getDatabase().getEntityManager().createQuery(
                    "select c from CloneChunk c where c.checksum = :checksum");

            query.setParameter("checksum", checksum);

            try {
                chunk = (CloneChunk) query.getSingleResult();
                logger.info("         Found chunk in DB: " + chunk);
            }
            catch (NoResultException e) {
                logger.info("         New chunk: " + checksum);

                chunk = new CloneChunk();
                chunk.setChecksum(checksum);

                try {
                    chunk.merge();
                }
                catch (Exception e1) {
                    logger.info("         RETRY for chunk" + checksum + ", because adding failed!! (try = " + tryCount + ")");
                    continue;
                }
                // TODO: can clash if two accounts index the same files at the same time
                // TODO: that's why we do a merge here!
            }

            break; // Success!
        }

        return chunk;
    }

    public void persist(Object... objects) {
        config.getDatabase().getEntityManager().getTransaction().begin();

        for (Object o : objects) {
            config.getDatabase().getEntityManager().persist(o);
        }

        config.getDatabase().getEntityManager().flush();
        config.getDatabase().getEntityManager().getTransaction().commit();
    }

    public void merge(Object... objects) {
        config.getDatabase().getEntityManager().getTransaction().begin();

        for (Object o : objects) {
            config.getDatabase().getEntityManager().merge(o);
        }

        config.getDatabase().getEntityManager().flush();
        config.getDatabase().getEntityManager().getTransaction().commit();
    }

    public List<CloneFile> getHistory(Profile profile) {
        Query q = config.getDatabase().getEntityManager().createQuery(
                "select c from CloneFile c where c.profileId = :profileId");
        //  and (c.syncStatus = :local or c.syncStatus = :inUpdate)");
        q.setParameter("profileId", profile.getId());
        //q.setParameter("local", CloneFile.SyncStatus.LOCAL);
        //q.setParameter("inUpdate", CloneFile.SyncStatus.IN_UPDATE);

        return q.getResultList();        
    }
}
