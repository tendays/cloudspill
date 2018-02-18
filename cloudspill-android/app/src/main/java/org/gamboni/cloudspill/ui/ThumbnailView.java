package org.gamboni.cloudspill.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.ItemType;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.job.DownloadStatus;
import org.gamboni.cloudspill.job.MediaDownloader;
import org.gamboni.cloudspill.job.ThumbnailIntentService;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

import java.io.File;

/**
 * @author tendays
 */

public class ThumbnailView extends AppCompatImageView implements ThumbnailIntentService.Callback {

    private static final String TAG = "CloudSpill.thumbs";

    private static final int UNSET = -1;

    private final MainActivity activity;
    private int position = UNSET;
    private int stateCounter = UNSET;
    private Domain.Item item = null;

    public ThumbnailView(MainActivity activity) {
        super(activity);
        this.activity = activity;

        this.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    public void setPosition(int stateCounter, final int position) {
        if (this.position == position && this.stateCounter == stateCounter) { return; }

        this.stateCounter = stateCounter;
        this.position = position;

        forceRefresh();
    }

    public void forceRefresh() {
        this.setImageBitmap(null);
        this.getOverlay().clear();
        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (item == null) { return; } // TODO Not threadsafe

                final FileBuilder file = item.getFile();
                Log.d(TAG, "Attempting to display "+ file);
                if (file.exists()) {
                    openExistingFile(file);
                } else {
                    // File doesn't exist - download it first
                    Log.d(TAG, "Item#"+ item.serverId +" not found - issuing download");


                    MediaDownloader.download(activity, item, new MediaDownloader.MediaListener() {
                        @Override
                        public void mediaReady(Uri location) {
                            openExistingFile(file);
                            // (location is using SAF which is unreliable) openItem(location, item.type.asMime());
                        }

                        @Override
                        public void notifyStatus(final DownloadStatus status) {
                            /* Handler (android.view.ViewRootImpl$ViewRootHandler) {23a21bd} sending message to a Handler on a dead thread
                                                                      java.lang.IllegalStateException: Handler (android.view.ViewRootImpl$ViewRootHandler) {23a21bd} sending message to a Handler on a dead thread
                                                                          at android.os.MessageQueue.enqueueMessage(MessageQueue.java:543)
                                                                          at android.os.Handler.enqueueMessage(Handler.java:631)
                                                                          at android.os.Handler.sendMessageAtTime(Handler.java:600)
                                                                          at android.os.Handler.sendMessageDelayed(Handler.java:570)
                                                                          at android.os.Handler.post(Handler.java:326)
                                                                          at android.view.ViewRootImpl.loadSystemProperties(ViewRootImpl.java:6929)
                                                                          at android.view.ViewRootImpl.<init>(ViewRootImpl.java:598)
                                                                          at android.view.WindowManagerGlobal.addView(WindowManagerGlobal.java:326)
                                                                          at android.view.WindowManagerImpl.addView(WindowManagerImpl.java:91)
                                                                          at android.widget.Toast$TN.handleShow(Toast.java:787)
                                                                          at android.widget.Toast$TN$1.run(Toast.java:679) */
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    switch (status) {
                                        case ERROR:
                                            Toast.makeText(activity, activity.getResources().getText(R.string.download_error), Toast.LENGTH_SHORT).show();
                                            return;
                                        case OFFLINE:
                                            Toast.makeText(activity, activity.getResources().getText(R.string.download_offline), Toast.LENGTH_SHORT).show();
                                            return;
                                    }

                                }
                            });
                        }
                    });
                }
            }

            private void openExistingFile(FileBuilder file) {
                // try the java.io.File, as it is more reliable
                File javaFile = file.getFileEquivalent();

                Log.d(TAG, "File exists. Java File equivalent: "+ javaFile);
                if (javaFile != null) {
                    openItem(Uri.fromFile(javaFile), item.type.asMime());
                } else {
                    openItem(file.getUri(), /*mime=auto*/null);
                }
            }
        });

        // Cancel any existing callback registered on the same view (note that callback equality is defined on the bitmap)
        // TODO [NICETOHAVE] Maybe the concept of 'target' should instead be explicit in the thumbnailIntentService API, so it could
        // auto-delete old tasks pointing to the same target
        ThumbnailIntentService.cancelCallback(this);
        ThumbnailIntentService.loadThumbnail(activity, position, this);
    }

    private void openItem(Uri uri, String mime) {
        Log.d(TAG, "Uri: "+ uri);
        Intent viewIntent = new Intent();
        viewIntent.setAction(Intent.ACTION_VIEW);
        if (mime == null) {
            viewIntent.setData(uri);
        } else {
            viewIntent.setDataAndType(uri, mime);
        }

        activity.startActivity(viewIntent);
    }

    @Override
    public void setItem(Domain.Item item) {
        this.item = item;
    }

    @Override
        public void setThumbnail(final Bitmap bitmap) {
        if (item == null) {
            // TODO find out why this happens
            Log.w(TAG, "item is not set!", new IllegalStateException());
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Drawable playIcon = activity.getDrawable(R.drawable.ic_play_circle_outline_black_24dp);
                playIcon.setBounds(0, 0, 100, 100);
                getOverlay().clear(); // hide "downloading" cloud
                if (item == null) {
                    // TODO find out why this happens
                    Log.w(TAG, "item is not set! (ui thread)");
                } else if (item.type == ItemType.VIDEO) {
                    getOverlay().add(playIcon);
                }
                ThumbnailView.this.setImageBitmap(bitmap);
            }
        });
    }

    private int getIcon(DownloadStatus status) {
        switch (status) {
            case DOWNLOADING:
                return R.drawable.ic_cloud_queue_black_24dp;
            case ERROR:
                return R.drawable.ic_cancel_black_24dp;
            case OFFLINE:
                return R.drawable.ic_signal_wifi_off_black_24dp;
        }
        throw new UnsupportedOperationException(status.name());
    }

    @Override
    public void setStatus(final DownloadStatus status) {

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Drawable playIcon = activity.getDrawable(
                        getIcon(status));
                playIcon.setBounds(0, 0, 100, 100);
                getOverlay().add(playIcon);
                ThumbnailView.this.setImageBitmap(null);
            }
        });
    }
}
