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

import org.syncany.config.Folder;
import org.syncany.util.FileLister.FileListerListener;
import org.syncany.util.FileUtil;
import java.io.File;
import org.syncany.Constants;

/**
 *
 * @author Philipp C. Heckel
 */
public class FileListerListenerImpl implements FileListerListener {
    private Folder root;
    private Indexer indexer;
    private boolean deleteIgnoreFiles;

    public FileListerListenerImpl(Folder root, Indexer indexer, boolean deleteIgnoreFiles) {
        this.root = root;
        this.indexer = indexer;
        this.deleteIgnoreFiles = deleteIgnoreFiles;
    }

    @Override
    public void proceedFile(File file) {
        System.err.println(file.getAbsoluteFile());
        indexer.index(root, file);
    }

    @Override
    public void enterDirectory(File directory) {
        indexer.index(root, directory);
    }

    @Override
    public boolean directoryFilter(File directory) {
        if (directory.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            if (deleteIgnoreFiles) {
                FileUtil.deleteRecursively(directory);
            }

            return false;
        }

        return true;
    }

    @Override
    public boolean fileFilter(File file) {
        if (file.getName().startsWith(Constants.FILE_IGNORE_PREFIX)) {
            if (deleteIgnoreFiles) {
                FileUtil.deleteRecursively(file);
            }

            return false;
        }

        return true;
    }

    @Override public void outDirectory(File directory) { }
    @Override public void startProcessing() { }
    @Override public void endOfProcessing() { }
}