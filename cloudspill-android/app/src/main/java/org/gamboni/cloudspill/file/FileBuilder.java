package org.gamboni.cloudspill.file;

import android.util.Log;

import java.io.File;
import java.io.IOException;

/** Helper class for constructing files by appending segments.
 * <p>This class makes sure no segment may go up in the file hierarchy.</p>
 * <p>NOTE: duplicate from CloudSpillServer.append() in the server module.</p>
 *
 * @author tendays
 */

public class FileBuilder {
    public final File target;
    private static final String TAG = "CloudSpill";

    public FileBuilder(String target) {
        this.target = new File(target);
    }

    public FileBuilder(File target) {
        this.target = target;
    }

    public FileBuilder append(String path) {
        try {
            File requested = new File(target, path).getCanonicalFile();
            if (!requested.getPath().startsWith(target.getCanonicalPath())) {
                return null;
            } else {
               return new FileBuilder(requested);
            }
        } catch (IOException io) {
            Log.w(TAG, "Client-provided path caused IOException: \"" + target + "\" / \"" + path + "\"", io);
            return null;
        }
    }

    public long getFreeSpace() {
        return target.getFreeSpace();
    }
}
