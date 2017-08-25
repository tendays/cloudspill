package org.gamboni.cloudspill.file;

import android.content.Context;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.File;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helper class for constructing files by appending segments.
 * <p>This class makes sure no segment may go up in the file hierarchy.</p>
 * <p>NOTE: duplicate from CloudSpillServer.append() in the server module.</p>
 * <p>This class is immutable. {@link #append(String)} returns a new instance,
 * leaving this unchanged.</p>
 *
 * @author tendays
 */

public abstract class FileBuilder {
    private static final String TAG = "CloudSpill";

    public static class NotFound extends FileBuilder {
        private final FileBuilder parent;
        private final String name;

        public NotFound(FileBuilder parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        @Override
        protected NotFound appendOne(String segment) {
            return new NotFound(this, segment);
        }

        @Override
        public FileBuilder getParent() {
            return parent;
        }

        @Override
        public Uri getUri() {
            return Uri.withAppendedPath(parent.getUri(), name);
        }

        @Override
        public boolean exists() {
            return false; // WARN the file might have been created since this got instantiated
        }

        @Override
        public boolean canWrite() {
            return true; // until proven otherwise...
        }

        @Override
        public boolean canRead() {
            return false;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public DocumentFile[] listFiles() {
            return null;
        }

        @Override
        public Date lastModified() {
            return new Date();
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public boolean delete() {
            return false;
        }
    }

    public static class Found extends FileBuilder {

        private final DocumentFile target;

        public Found(Context context, Uri target) {
            this(DocumentFile.fromTreeUri(context, target));
        }

        public Found(DocumentFile target) {
            this.target = target;
        }

        @Override
        protected FileBuilder appendOne(String segment) {
            DocumentFile requested = target.findFile(segment);
            if (requested == null) {
                return new NotFound(this, segment);
            } else {
                return new Found(requested);
            }
        }

        @Override
        public FileBuilder getParent() {
            DocumentFile parent = target.getParentFile();
            // TODO 1: check File.getParentFile may actually return null. 2: return a NotFound instead
            if (parent == null) {
                throw new IllegalStateException(this +".getParent() returned null");
            } else {
                return new Found(parent);
            }
        }

        @Override
        public Uri getUri() {
            return target.getUri();
        }

        @Override
        public boolean exists() {
            return target.exists();
        }

        @Override
        public boolean canWrite() {
            return target.canWrite();
        }

        @Override
        public boolean canRead() {
            return target.canRead();
        }

        @Override
        public String getName() {
            return target.getName();
        }

        @Override
        public DocumentFile[] listFiles() {
            return target.listFiles();
        }

        @Override
        public Date lastModified() {
            return new Date(target.lastModified());
        }

        @Override
        public long length() {
            return target.length();
        }

        @Override
        public boolean delete() {
            return target.delete();
        }
    }

    public FileBuilder append(String path) {
        //Log.d(TAG, this + " .append(" + path + ")");
        FileBuilder pointer = this;
        int cursor = -1;
        int next;
        while ((next = path.indexOf('/', cursor + 1)) != -1) {
            pointer = pointer.appendOne(path.substring(cursor + 1, next));
            cursor = next;
        }

        return pointer.appendOne(path.substring(cursor + 1));
    }

    protected abstract FileBuilder appendOne(String segment);

    public abstract FileBuilder getParent();

    public abstract Uri getUri();

    public String getRelativePath(DocumentFile child) {
        String targetPath = getUri().getPath();
        if (!targetPath.endsWith("/")) {
            targetPath += "/";
        }
        String childPath = child.getUri().toString();
        if (!childPath.startsWith(targetPath)) {
            throw new IllegalArgumentException("Given file "+ child +" is not under "+ targetPath);
        }
        return childPath.substring(targetPath.length());
    }

    private static final String FILE_URI = "file://";

    private static final Pattern FILE_EQUIVALENT_URI = Pattern.compile(
            "content://com.android.externalstorage.documents/tree/([^/]*)/document/\\1(.*)");

    private static final String PRIMARY_STORAGE_NAME = "primary:";
    public synchronized File getFileEquivalent() {
        String uriString = getUri().toString();
        Matcher matcher = FILE_EQUIVALENT_URI.matcher(uriString);
        if (matcher.matches()) {
            String folder = Uri.decode(matcher.group(1));
            String path = Uri.decode(matcher.group(2));
            if (folder.startsWith(PRIMARY_STORAGE_NAME)) {
                return new File("/storage/emulated/0/" + folder.substring(PRIMARY_STORAGE_NAME.length()) + path);
            } else {
// content://com.android.externalstorage.documents/tree/3563-3866%3ADCIM/document/3563-3866%3ADCIM%2F100MEDIA%2FIMAG1013.jpg
// LOCAL_STORAGE_URI ----------------------------------><-------cut this--------->
                return new File("/storage/" + folder.replace(':', '/') + path);
            }
        } else if (uriString.startsWith(FILE_URI)) {
            return new File(uriString.substring(FILE_URI.length()));
        } else {
            Log.d(TAG, "Can't determine File equivalent of "+ this);
            return null;
        }
    }

    public abstract boolean exists();

    public abstract boolean canWrite();

    public abstract boolean canRead();

    public abstract DocumentFile[] listFiles();

    public abstract String getName();

    public abstract Date lastModified();

    public abstract long length();

    public abstract boolean delete();

    public Found mkdirs() {
        if (!exists()) {
            FileBuilder parent = getParent();
            Found foundParent = parent.mkdirs();
            return new Found(foundParent.target.createDirectory(getName()));
        } else {
            return (Found)this; // NotFound.exists always returns false so we must be Found
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
        Log.d(TAG, "File equivalent of "+ this +" is "+ file + (file == null ? "" : (" with "+ file.getUsableSpace() +" bytes usable.")));
        if (file != null && file.getUsableSpace() == 0) {
            file = file.getParentFile();
            Log.d(TAG, "Using parent file "+ file);
        }
        if (file == null || file.getUsableSpace() == 0) {
            throw new IllegalStateException();
        }
        return (file == null) ? 0L : Math.max(0L, (required - file.getUsableSpace()));
    }

    public int hashCode() {
        return getUri().hashCode();
    }

    public boolean equals(Object o) {
        // NOTE: we don't expect to mix FileBuilders with nulls or other types. Fail early if that happens
        // Also note that Found and NotFound may be equal if they represent the same file
        return ((FileBuilder)o).getUri().equals(this.getUri());
    }

    public String toString() {
        return getUri() + " [" + getClass().getSimpleName() + "]";
    }
}
