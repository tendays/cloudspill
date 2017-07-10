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

    public String getRelativePath(File child) {
        String targetPath = target.getPath();
        if (!target.getPath().endsWith("/")) {
            targetPath += "/";
        }
        if (!child.getPath().startsWith(targetPath)) {
            throw new IllegalArgumentException("Given file "+ child +" is not under "+ targetPath);
        }
        return child.getPath().substring(target.getPath().length());
    }

    File filesystemRoot = null;
    private synchronized File getFilesystemRoot() {
        if (filesystemRoot == null) {
            File pointer = target;
            while (pointer != null && pointer.getUsableSpace() == 0) {
                pointer = pointer.getParentFile();
            }
            filesystemRoot = (pointer == null) ? target : pointer;
        }
        return filesystemRoot;
    }

    public long getFreeSpace() {
        return getFilesystemRoot().getUsableSpace();
    }

    public String toString() {
        return target.toString();
    }
}
