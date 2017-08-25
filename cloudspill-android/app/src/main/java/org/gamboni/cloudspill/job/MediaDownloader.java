package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

import java.io.IOException;
import java.io.OutputStream;

/** This IntentService is responsible for downloading pictures that spring into view in the ui.
 * @author tendays
 */
public class MediaDownloader extends IntentService {
    private static final String TAG = "CloudSpill.MD";

    private static final String PARAM_SERVER_ID = "serverId";
    private static final String PARAM_FILE = "file";

    private static final SettableMediaListener statusListener = new SettableMediaListener();

    public interface MediaListener extends StatusReport {
        void mediaReady(long serverId, Uri location);
    }

    private static class SettableMediaListener extends SettableStatusListener<MediaListener> implements MediaListener {
        public void mediaReady(long serverId, Uri location) {
            MediaListener delegate = listener;
            if (delegate != null) {
                delegate.mediaReady(serverId, location);
            }
        }
    }

    Domain domain = new Domain(this);

    public static void download(Context context, Domain.Item item) {
        Intent intent = new Intent(context, MediaDownloader.class);
        intent.putExtra(MediaDownloader.PARAM_SERVER_ID, item.serverId);
        intent.putExtra(MediaDownloader.PARAM_FILE, item.getFile().getUri());
        context.startService(intent);
    }

    public MediaDownloader() {
        super(MediaDownloader.class.getName());
    }

    public static void setStatusListener(MediaListener listener) {
        statusListener.set(listener);
    }

    public static void unsetStatusListener(MediaListener listener) {
        statusListener.unset(listener);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // TODO allow actually setting status report..
        CloudSpillServerProxy server = CloudSpillServerProxy.selectServer(this, statusListener, domain);
        if (server == null) { return; } // offline


        final long serverId = intent.getLongExtra(PARAM_SERVER_ID, 0);
        final FileBuilder target = new FileBuilder.Found(DocumentFile.fromTreeUri(this, intent.<Uri>getParcelableExtra(PARAM_FILE)));
        Log.d(TAG, "Downloading item "+ serverId +" to "+ target);
        // Make sure directory exists
        FileBuilder parent = target.getParent();
        parent.mkdirs();
        if (!parent.canWrite()) {
            statusListener.updateMessage(StatusReport.Severity.ERROR, "Download directory not writable: "+ parent);
        }

        server.download(serverId, target,
                new Response.Listener<byte[]>() {
                    @Override
                    public void onResponse(byte[] response) {
                        Log.d(TAG, "Received item "+ serverId);
                        OutputStream o = null;
                        try {
                            o = getContentResolver().openOutputStream(target.getUri());
                            o.write(response);
                        } catch (IOException e) {
                            Log.e(TAG, "Writing "+ serverId +" to "+ target +" failed", e);
                            statusListener.updateMessage(StatusReport.Severity.ERROR, "Media storage error: "+ e);
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
                        statusListener.mediaReady(serverId, target.getUri());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Failed downloading item "+ serverId, error);
                        statusListener.updateMessage(StatusReport.Severity.ERROR, "Media download error: "+ error);
                    }
                }
        );
    }
}
