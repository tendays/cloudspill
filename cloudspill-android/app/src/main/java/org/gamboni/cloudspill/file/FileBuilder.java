package org.gamboni.cloudspill.file;

import android.content.Context;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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
    protected final Context context;

    public static class FileBased extends FileBuilder {
        private final File file;
        public FileBased(Context context, File file) {
            super(context);
            this.file = file;
        }
        @Override
        protected FileBuilder appendOne(String segment, boolean doc) {
            return new FileBased(context, new File(this.file, segment));
        }

        @Override
        public FileBuilder getParent() {
            return new FileBased(context, file.getParentFile());
        }

        @Override
        public Uri getUri() {
            return Uri.fromFile(file);
        }

        @Override
        public boolean exists() {
            return file.exists();
        }

        @Override public boolean isDirectory() { return file.isDirectory(); }

        @Override
        public void mkdirs() {
            file.mkdirs();
        }

        @Override
        public InputStream read() throws FileNotFoundException {
            return new FileInputStream(file);
        }

        @Override
        public OutputStream write(Context context, String mime) throws FileNotFoundException {
            return new FileOutputStream(file);
        }

        @Override
        public boolean canWrite() {
            return file.canWrite();
        }

        @Override
        public boolean canRead() {
            return file.canRead();
        }

        @Override
        public List<FileBuilder> listFiles() {
            return new AbstractList<FileBuilder>() {
                File[] files = file.listFiles();

                public int size() {
                    return files.length;
                }

                public FileBuilder get(int index) {
                    return new FileBased(context, files[index]);
                }
            };
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public Date lastModified() {
            return new Date(file.lastModified());
        }

        @Override
        public long length() {
            return file.length();
        }

        @Override
        public boolean delete() {
            return file.delete();
        }
    }

    public static class NotFound extends FileBuilder {
        private final FileBuilder parent;
        private final String name;

        public NotFound(FileBuilder parent, String name) {
            super(parent.context);
            this.parent = parent;
            this.name = name;
        }

        @Override
        protected NotFound appendOne(String segment, boolean doc) {
            return new NotFound(this, segment);
        }

        public void mkdirs() {
            throw new UnsupportedOperationException();
/*            if (!exists()) {
                FileBuilder parent = getParent();
                parent.mkdirs();
                return new Found(context, foundParent.target.createDirectory(getName()));
            }
  */      }

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

        @Override public boolean isDirectory() { return false; }

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
        public List<FileBuilder> listFiles() {
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

        @Override
        public InputStream read() throws FileNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutputStream write(Context context, String mime) throws FileNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class Found extends FileBuilder {
        private final DocumentFile target;

        public Found(Context context, Uri target) {
            this(context, DocumentFile.fromTreeUri(context, target));
        }

        public Found(Context context, DocumentFile target) {
            super(context);
            this.target = target;
        }

        @Override
        protected FileBuilder appendOne(String segment, boolean doc) {
            Uri uri = Uri.parse(target.getUri() + Uri.encode("/"+ segment));
                    // inserts unencoded slashes, which is apparently not ok? Uri.withAppendedPath(target.getUri(), segment);
            DocumentFile requested = /*doc ? */ DocumentFile.fromSingleUri(context, uri) /*: DocumentFile.fromTreeUri(context, uri)*/;
            if (requested == null) {
                return new NotFound(this, segment);
            } else {
                return new Found(context, requested);
            }
        }

        @Override
        public void mkdirs() {
            if (!exists()) {
                Found parent = getParent();
                parent.mkdirs();
                parent.target.createDirectory(getName());
            }
        }

        @Override
        public Found getParent() {
            DocumentFile parent = target.getParentFile();
            // NOTE maybe we should just always do the below uri manipulation
            if (parent == null) {
                String uriString = getUri().toString();

                int slash = Math.max(uriString.lastIndexOf("%2F"), uriString.lastIndexOf("/"));
                if (slash != -1) {
                    return new Found(context, DocumentFile.fromTreeUri(context, Uri.parse(uriString.substring(0, slash))));
                }
                throw new IllegalStateException(this +".getParent() returned null, path contains no slash");
            } else {
                return new Found(context, parent);
            }
        }

        public String getName() {
            String uriString = Uri.decode(getUri().toString());

            int slash = uriString.lastIndexOf("/")+1;
            if (slash == -1) { throw new IllegalStateException("Can't create "+ this +": could not isolate name component"); }
            return uriString.substring(slash);
        }

        @Override
        public InputStream read() throws FileNotFoundException {
            return context.getContentResolver().openInputStream(getUri());
        }

        public OutputStream write(Context context, String mime) throws FileNotFoundException {
            final Uri uri;
            if (exists()) {
                uri = target.getUri();
                Log.d(TAG, "Writing to existing uri "+ uri);
            } else {
                Found parent = getParent();
                parent.mkdirs();

                DocumentFile created = parent.target.createFile(mime, getName());
                if (created == null) { throw new IllegalArgumentException("Failed creating "+ this); }
                uri = created.getUri();
                Log.d(TAG, "Writing to newly created uri "+ uri +" (initially "+ target.getUri() +")");
            }
            return context.getContentResolver().openOutputStream(uri);
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
        public boolean isDirectory() { return target.isDirectory(); }

        @Override
        public boolean canWrite() {
            return target.canWrite();
        }

        @Override
        public boolean canRead() {
            return target.canRead();
        }

        @Override
        public List<FileBuilder> listFiles() {
            return new AbstractList<FileBuilder>() {
                final DocumentFile[] files = target.listFiles();

                public int size() {
                    return files.length;
                }

                public FileBuilder get(int index) {
                    return new Found(context, files[index]);
                }
            };
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

    protected FileBuilder(Context context) {
        this.context = context;
    }

    public FileBuilder append(String path) {
        FileBuilder pointer = this;
        int cursor = -1;
        int next;
        while ((next = path.indexOf('/', cursor + 1)) != -1) {
            pointer = pointer.appendOne(path.substring(cursor + 1, next), false);
            cursor = next;
        }
        pointer = pointer.appendOne(path.substring(cursor + 1), true);

        return pointer;
    }

    public abstract InputStream read() throws FileNotFoundException;

    protected abstract FileBuilder appendOne(String segment, boolean doc);

    public abstract FileBuilder getParent();

    public abstract Uri getUri();

    public String getRelativePath(FileBuilder child) {
        String targetPath = getUri().getPath();
        if (!targetPath.endsWith("/")) {
            targetPath += "/";
        }
        String childPath = child.getUri().getPath();
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

    public abstract boolean isDirectory();

    public abstract boolean canWrite();

    public abstract boolean canRead();

    public abstract List<FileBuilder> listFiles();

    public abstract String getName();

    public abstract Date lastModified();

    public abstract long length();

    public abstract boolean delete();

    public abstract OutputStream write(Context context, String mime) throws FileNotFoundException;

    public abstract void mkdirs();

    /** Return how many bytes are missing in order to be able to store the given number of bytes.
     * This only returns a non-zero value for local storage.
     *
     * @param required number of bytes required
     * @return number of bytes missing to reach required
     */
    public long getMissingSpace(long required) {
        File file = getFileEquivalent();
        //Log.d(TAG, "File equivalent of "+ this +" is "+ file + (file == null ? "" : (" with "+ file.getUsableSpace() +" bytes usable.")));
        while (file != null && file.getUsableSpace() == 0) {
            file = file.getParentFile();
            //Log.d(TAG, "Using parent file "+ file);
        }
        if (file == null) {
            throw new IllegalStateException(this.toString() + " -> " + file); // TODO return 0 instead
        }
        return Math.max(0L, (required - file.getUsableSpace()));
    }

    public int hashCode() {
        return getUri().hashCode();
    }

    public boolean equals(Object o) {
        // Note that Found and NotFound may be equal if they represent the same file
        return (o instanceof FileBuilder) && ((FileBuilder)o).getUri().equals(this.getUri());
    }

    public String toString() {
        return getUri() + " [" + getClass().getSimpleName() + "]";
    }
}
