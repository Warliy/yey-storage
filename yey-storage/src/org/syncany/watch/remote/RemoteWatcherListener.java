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

import org.syncany.db.CloneFile.SyncStatus;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.syncany.config.Profile;
import org.syncany.repository.Update;

/**
 *
 * @author Philipp C. Heckel
 */
public interface RemoteWatcherListener {
    void onUpdateProcessingStart(Profile profile);
    void onUpdateProcessingEnd(Profile profile, Map<Long, List<Update>> appliedUpdates);
    void onLocalFileStatusChanged(Profile profile, File file, SyncStatus syncStatus);
}
