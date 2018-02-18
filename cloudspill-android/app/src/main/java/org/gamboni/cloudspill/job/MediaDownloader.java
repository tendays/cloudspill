package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.StorageFailedException;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** This IntentService is responsible for downloading pictures that spring into view in the ui.
 * @author tendays
 */
public class MediaDownloader extends IntentService {
    private static final String TAG = "CloudSpill.MD";

    private static final String PARAM_SERVER_ID = "serverId";
    private static final String PARAM_FILE = "file";

    private static final SettableStatusListener<StatusReport> statusListener = new SettableStatusListener<>();

    private static final Map<Long, Set<MediaListener>> callbacks = new HashMap<>();

    public interface MediaListener {
        void mediaReady(Uri location);
        void notifyStatus(DownloadStatus status);
    }

    Domain domain = new Domain(this);

    public static void download(Context context, Domain.Item item, MediaListener callback) {
        Intent intent = new Intent(context, MediaDownloader.class);
        intent.putExtra(MediaDownloader.PARAM_SERVER_ID, item.serverId);
        Uri uri = item.getFile().getUri();
        intent.putExtra(MediaDownloader.PARAM_FILE, uri);

         if (DocumentFile.fromSingleUri(context, uri).exists()) {
             throw new IllegalStateException(uri +" already exists");
         }

         synchronized (callbacks) {
             Set<MediaListener> set = callbacks.get(item.serverId);
             if (set == null) {
                 set = new HashSet<>();
                 callbacks.put(item.serverId, set);
                 set.add(callback);
             } else {
                 set.add(callback);
                 // existing download already running, will invoke the callback when ready
                 return;
             }
         }

        context.startService(intent);
    }

    public MediaDownloader() {
        super(MediaDownloader.class.getName());
    }

    public static void setStatusListener(StatusReport listener) {
        statusListener.set(listener);
    }

    public static void unsetStatusListener(StatusReport listener) {
        statusListener.unset(listener);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final long serverId = intent.getLongExtra(PARAM_SERVER_ID, 0);
        final Uri uri = intent.getParcelableExtra(PARAM_FILE);

        CloudSpillServerProxy server = CloudSpillServerProxy.selectServer(this, statusListener, domain);
        if (server == null) { // offline
            notifyStatus(serverId, DownloadStatus.OFFLINE);
            return;
        }

        final FileBuilder target;
        String FILE_URI = "file://";
        if (uri.toString().startsWith(FILE_URI)) {
            target = new FileBuilder.FileBased(this, new File(uri.toString().substring(FILE_URI.length())));
        } else {
            target = new FileBuilder.Found(this, DocumentFile.fromSingleUri(this, uri));
            if (target.exists()) {
                throw new IllegalStateException(uri +" already exists");
            }
        }
        Log.d(TAG, "Downloading item "+ serverId +" to "+ target);
        // Make sure directory exists
        FileBuilder parent = target.getParent();
        try {
            Log.i(TAG, "Directory does not exist, trying to create: "+ parent);
            parent.mkdirs();
        } catch (StorageFailedException e) {
            notifyError(serverId, e.getMessage());
            return;
        }
        if (!parent.canWrite()) {
            notifyError(serverId, "Download directory not writable: "+ parent);
            return;
        }

        server.stream(serverId,
                new Response.Listener<InputStream>() {
                    @Override
                    public void onResponse(InputStream response) {
                        Log.d(TAG, "Received item "+ serverId);
                        OutputStream o = null;
                        try {
                            o = target.write(MediaDownloader.this, "image/jpeg");
                            byte[] buf = new byte[4096];
                            int len;
                            while ((len = response.read(buf)) > 0) {
                                o.write(buf, 0, len);
                            }
                        } catch (StorageFailedException|IOException e) {
                            Log.e(TAG, "Writing "+ serverId +" to "+ target +" failed");
                            // Delete partially downloaded file otherwise next time it won't reattempt download
                            target.delete();
                            notifyError(serverId, "Media storage error", e);
                            return;
                        } finally {
                            if (o != null) {
                                try {
                                    o.close();
                                } catch (IOException e) {
                                    /* ignore */
                                }
                            }
                        }
                        /* At this point the file has been successfully downloaded. */
                        final Set<MediaListener> set;
                        synchronized (callbacks) {
                            set = callbacks.remove(serverId);
                        }
                        for (MediaListener callback : set) {
                            callback.mediaReady(target.getUri());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        notifyStatus(serverId, DownloadStatus.ERROR);
                        Log.e(TAG, "Failed downloading item "+ serverId, error);
                        statusListener.updateMessage(StatusReport.Severity.ERROR, "Media download error: "+ error);
                    }
                }
        );
    }

    /** Notify callbacks of the given server id of the download status. WARN this removes
     * all callbacks from the map, IOW this should be considered to end the conversation with the callback.
     * @param serverId server id of the item being downloaded
     * @param status download status
     */
    private void notifyStatus(long serverId, DownloadStatus status) {
        final Set<MediaListener> set;
        synchronized (callbacks) {
            set = callbacks.remove(serverId);
        }
        for (MediaListener callback : set) {
            callback.notifyStatus(status);
        }
    }

    private void notifyError(long serverId, String message) {
        notifyStatus(serverId, DownloadStatus.ERROR);
        statusListener.updateMessage(StatusReport.Severity.ERROR, message);

    }

    private void notifyError(long serverId, String message, Throwable cause) {
        notifyError(serverId, message +" ("+ cause.getMessage() +")");
        Log.e(TAG, message, cause);
    }
}
