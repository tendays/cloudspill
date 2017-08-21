package org.gamboni.cloudspill.file;

import android.content.Context;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.File;
import java.io.UnsupportedEncodingException;

/** Helper class for constructing files by appending segments.
 * <p>This class makes sure no segment may go up in the file hierarchy.</p>
 * <p>NOTE: duplicate from CloudSpillServer.append() in the server module.</p>
 * <p>This class is immutable. {@link #append(String)} returns a new instance,
 * leaving this unchanged.</p>
 *
 * @author tendays
 */

public class FileBuilder {
    public final DocumentFile target;
    private static final String TAG = "CloudSpill";

    public FileBuilder(Context context, Uri target) {
        this(DocumentFile.fromTreeUri(context, target));
    }

    public FileBuilder(DocumentFile target) {
        this.target = target;
    }

    public FileBuilder append(String path) {
            DocumentFile pointer = target;
            int cursor = 0;
            int next;
            while ((next = path.indexOf('/', cursor)) != -1) {
                DocumentFile requested = pointer.findFile(path.substring(cursor + 1, next));
                if (requested == null) {
                    return null;
                }
            }
            DocumentFile requested = pointer.findFile(path.substring(cursor + 1));
            if (requested == null) { return null; }

            return new FileBuilder(requested);
    }

    public FileBuilder getParent() {
        DocumentFile parent = target.getParentFile();
        // TODO 1: check File.getParentFile may actually return null. 2: consider using Null Object instead of null
        return (parent == null) ? null : new FileBuilder(parent);
    }

    public String getRelativePath(DocumentFile child) {
        String targetPath = target.getUri().toString();
        if (!targetPath.endsWith("/")) {
            targetPath += "/";
        }
        String childPath = child.getUri().toString();
        if (!childPath.startsWith(targetPath)) {
            throw new IllegalArgumentException("Given file "+ child +" is not under "+ targetPath);
        }
        return childPath.substring(targetPath.length());
    }

    private static final String LOCAL_STORAGE_URI = "content://com.android.externalstorage.documents/tree/";
    private static final String PRIMARY_STORAGE_NAME = "primary/";
    private File filesystemRoot = null;
    public synchronized File getFileEquivalent() {
        if (target.getUri().toString().startsWith(LOCAL_STORAGE_URI)) {
                String path = Uri.decode(target.getUri().toString().substring(LOCAL_STORAGE_URI.length()));
                if (path.startsWith(PRIMARY_STORAGE_NAME)) {
                    return new File("/storage/emulated/0/" + path.substring(PRIMARY_STORAGE_NAME.length()));
                } else {
                    return new File("/storage/"+ path);
                }
        } else {
            Log.d(TAG, "Can't determine File equivalent of "+ this);
            return null;
        }
    }

    public void mkdirs() {
        if (!target.exists()) {
            FileBuilder parent = getParent();
            parent.mkdirs();
            parent.target.createDirectory(target.getName());
        }
    }

    /** Return how many bytes are missing in order to be able to store the given number of bytes.
     * This only returns a non-zero value for local storage.
     *
     * @param required number of bytes required
     * @return number of bytes missing to reach required
     */
    public long getMissingSpace(long required) {
        File file = getFileEquivalent();
        return (file == null) ? 0L : Math.max(0L, (required - file.getUsableSpace()));
    }

    public int hashCode() {
        return target.hashCode();
    }

    public boolean equals(Object o) {
        // NOTE: we don't expect to mix FileBuilders with nulls or other types. Fail early if that happens
        return ((FileBuilder)o).target.equals(this.target);
    }

    public String toString() {
        return target.toString();
    }
}
