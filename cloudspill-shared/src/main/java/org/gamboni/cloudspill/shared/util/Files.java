/**
 * 
 */
package org.gamboni.cloudspill.shared.util;

import java.io.File;
import java.io.IOException;

/**
 * @author tendays
 *
 */
public abstract class Files {

    public static File append(File parent, String child) {
    	if (parent == null) { return null; } // indicates earlier error
		try {
			File requested = new File(parent, child).getCanonicalFile();
			if (!requested.getPath().startsWith(parent.getCanonicalPath())) {
				return null;
			} else {
				return requested;
			}
		} catch (IOException io) {
			Log.warn("Client-provided path caused IOException: \"" + parent + "\" / \"" + child + "\"", io);
			return null;
    	}
    }
}
