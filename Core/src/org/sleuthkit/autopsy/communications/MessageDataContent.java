/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.beans.PropertyChangeEvent;
import org.openide.explorer.ExplorerManager;
import org.sleuthkit.autopsy.contentviewers.MessageContentViewer;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;

/**
 * Extends MessageContentViewer so that it implements DataContent and can be set
 * as the only ContentViewer for a DataResultPanel
 */
final class MessageDataContent extends MessageContentViewer implements DataContent, ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private ExplorerManager em = new ExplorerManager();

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }
}
