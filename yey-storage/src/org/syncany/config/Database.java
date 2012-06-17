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
package org.syncany.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.syncany.Constants;
import org.syncany.exceptions.ConfigException;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Database implements Configurable {
    /* private static final Config config = Config.getInstance();
     * 
     * WARNING: Do NOT add 'Config' as a static final here. 
     *          Since this class is created in the Config constructor, 
     *          Config.getInstance() will return NULL.
     */

    private Properties properties;
    private EntityManagerFactory entityManagerFactory;
    private Map<Long, EntityManager> entityManagers;

    public Database() {
        properties = new Properties();
        entityManagers = new HashMap<Long, EntityManager>();
    }

    public synchronized EntityManager getEntityManager() {
        Long threadId = Thread.currentThread().getId();
        EntityManager entityManager = entityManagers.get(threadId);

        if (entityManager == null) {
            entityManager = entityManagerFactory.createEntityManager();
            entityManagers.put(threadId, entityManager);
        }

        return entityManager;
    } 

    @Override
    public void load(ConfigNode node) throws ConfigException {
        File dbFileName = new File(Config.getInstance().getConfDir()+File.separator+Constants.CONFIG_DATABASE_DIRNAME+File.separator+Constants.CONFIG_DATABASE_FILENAME);
        
        // Override database location with [confdir]/db/syncany
        properties.setProperty(PersistenceUnitProperties.JDBC_URL,
            String.format(Constants.CONFIG_DATABASE_URL_FORMAT, dbFileName.getAbsolutePath()));

        // Adjust generation strategy
        // - if DB folder exists, we assume the tables have been created
        // - if not, we need to create them
        if (!dbFileName.exists()) {
            properties.setProperty(PersistenceUnitProperties.DDL_GENERATION, PersistenceUnitProperties.CREATE_ONLY);
        }        
        else {
            properties.setProperty(PersistenceUnitProperties.DDL_GENERATION, PersistenceUnitProperties.NONE);
        }        
        
        // Override other values (if defined in config!)
        if (node != null) {
            for (ConfigNode property : node.findChildrenByXpath("property")) {
                properties.setProperty(property.getAttribute("name"), property.getAttribute("value"));
            }
        }
        
        // Load!
        entityManagerFactory = Persistence.createEntityManagerFactory(Constants.CONFIG_DATABASE_PERSISTENCE_UNIT, properties);
    }

    @Override
    public void save(ConfigNode node) {
        // NOTHING TO DO! CANNOT BE ALTERED BY APPLICATION!
    }

}
