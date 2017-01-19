/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.modules.interestingitems;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.openide.util.NbBundle;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.MetaTypeCondition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Provides access to collections of FilesSet definitions persisted to disk.
 * Clients receive copies of the most recent FilesSet definitions for
 * Interesting Items or File Ingest Filters via synchronized methods, allowing
 * the definitions to be safely published to multiple threads.
 */
public final class FilesSetsManager extends Observable {

    @NbBundle.Messages({"FilesSetsManager.allFilesAndDirectories=All Files and Directories",
        "FilesSetsManager.allFilesDirectoriesAndUnallocated=All Files, Directories, and Unallocated Space"})
    private static final List<String> ILLEGAL_FILE_NAME_CHARS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("\\", "/", ":", "*", "?", "\"", "<", ">")));
    private static final List<String> ILLEGAL_FILE_PATH_CHARS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("\\", ":", "*", "?", "\"", "<", ">")));
    private static final String LEGACY_FILES_SET_DEFS_FILE_NAME = "InterestingFilesSetDefs.xml"; //NON-NLS
    private static final String INTERESTING_FILES_SET_DEFS_NAME = "InterestingFileSets.settings";
    private static final String FILE_INGEST_FILTER_DEFS_NAME = "FileIngestFilterDefs.settings";
    private static final Object FILE_INGEST_FILTER_LOCK = new Object();
    private static final Object INTERESTING_FILES_SET_LOCK = new Object();
    private static FilesSetsManager instance;
    private static final FilesSet FILES_DIRS_INGEST_FILTER = new FilesSet(
            Bundle.FilesSetsManager_AllFilesAndDirectories(), Bundle.FilesSetsManager_AllFilesAndDirectories(), false, true, new HashMap<String, Rule>() {
        {
            put(Bundle.FilesSetsManager_AllFilesAndDirectories(),
                    new Rule(Bundle.FilesSetsManager_AllFilesAndDirectories(), null,
                            new MetaTypeCondition(MetaTypeCondition.Type.ALL), null, null, null));
        }
    }); //WJS-TODO make this an @MESSAGES//NON-NLS
    private static final FilesSet FILES_DIRS_UNALLOC_INGEST_FILTER = new FilesSet(
            Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(), Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(),
            false, false, new HashMap<String, Rule>() {
        {
            put(Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(),
                    new Rule(Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(), null,
                            new MetaTypeCondition(MetaTypeCondition.Type.ALL), null, null, null));
        }
    }); //WJS-TODO make this an @MESSAGES//NON-NLS

    /**
     * Gets the FilesSet definitions manager singleton.
     */
    public synchronized static FilesSetsManager getInstance() {
        if (instance == null) {
            instance = new FilesSetsManager();
        }
        return instance;
    }

    /**
     * Gets the set of chars deemed to be illegal in file names (Windows).
     *
     * @return A list of characters.
     */
    static List<String> getIllegalFileNameChars() {
        return FilesSetsManager.ILLEGAL_FILE_NAME_CHARS;
    }

    /**
     * Gets the set of chars deemed to be illegal in file path
     * (SleuthKit/Windows).
     *
     * @return A list of characters.
     */
    static List<String> getIllegalFilePathChars() {
        return FilesSetsManager.ILLEGAL_FILE_PATH_CHARS;
    }

    /**
     * Get a list of default FileIngestFilters.
     *
     * @return a list of FilesSets which cover default options.
     */
    public static List<FilesSet> getStandardFileIngestFilters() {
        return Arrays.asList(FILES_DIRS_UNALLOC_INGEST_FILTER, FILES_DIRS_INGEST_FILTER);
    }

    /**
     * Get the filter that should be used as the default value, if no filter is
     * specified.
     *
     * @return FILES_DIRS_UNALLOC_INGEST_FILTER
     */
    public static FilesSet getDefaultFilter() {
        return FILES_DIRS_UNALLOC_INGEST_FILTER;
    }

    /**
     * Gets a copy of the current interesting files set definitions.
     *
     * @return A map of interesting files set names to interesting file sets,
     *         possibly empty.
     */
    Map<String, FilesSet> getInterestingFilesSets() throws FilesSetsManagerException {
        synchronized (INTERESTING_FILES_SET_LOCK) {
            return InterestingItemsFilesSetSettings.readDefinitionsFile(INTERESTING_FILES_SET_DEFS_NAME, LEGACY_FILES_SET_DEFS_FILE_NAME);
        }
    }

    /**
     * Gets a copy of the current ingest file set definitions.
     *
     * The defaults are not included so that they will not show up in the
     * editor.
     *
     * @return A map of FilesSet names to file ingest sets, possibly empty.
     */
    public Map<String, FilesSet> getCustomFileIngestFilters() throws FilesSetsManagerException {
        synchronized (FILE_INGEST_FILTER_LOCK) {
            Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), FILE_INGEST_FILTER_DEFS_NAME);
            File fileSetFile = filePath.toFile();
            String filePathStr = filePath.toString();
            if (fileSetFile.exists()) {
                try {
                    try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePathStr))) {
                        Map<String, FilesSet> filesSetsSettings = (Map<String, FilesSet>) in.readObject();
                        return filesSetsSettings;
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    throw new FilesSetsManagerException(String.format("Failed to read settings from %s", filePathStr), ex);
                }
            } else {
                return new HashMap<>();
            }
        }
    }

    /**
     * Sets the current interesting file sets definitions, replacing any
     * previous definitions.
     *
     * @param filesSets A mapping of interesting files set names to files sets,
     *                  used to enforce unique files set names.
     */
    void setInterestingFilesSets(Map<String, FilesSet> filesSets) throws FilesSetsManagerException {
        synchronized (INTERESTING_FILES_SET_LOCK) {
            InterestingItemsFilesSetSettings.writeDefinitionsFile(INTERESTING_FILES_SET_DEFS_NAME, filesSets);
            this.setChanged();
            this.notifyObservers();
        }
    }

    /**
     * Sets the current interesting file sets definitions, replacing any
     * previous definitions.
     *
     * @param filesSets A mapping of file ingest filters names to files sets,
     *                  used to enforce unique files set names.
     */
    void setCustomFileIngestFilters(Map<String, FilesSet> filesSets) throws FilesSetsManagerException {
        synchronized (FILE_INGEST_FILTER_LOCK) {
            try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(Paths.get(PlatformUtil.getUserConfigDirectory(), FILE_INGEST_FILTER_DEFS_NAME).toString()))) {
                out.writeObject(filesSets);
            } catch (IOException ex) {
                throw new FilesSetsManagerException(String.format("Failed to write settings to %s", FILE_INGEST_FILTER_DEFS_NAME), ex);
            }
        }
    }

    public static class FilesSetsManagerException extends Exception {

        FilesSetsManagerException() {

        }

        FilesSetsManagerException(String message) {
            super(message);
        }

        FilesSetsManagerException(String message, Throwable cause) {
            super(message, cause);
        }

        FilesSetsManagerException(Throwable cause) {
            super(cause);
        }
    }

}
