package org.gamboni.cloudspill.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.job.ThumbnailIntentService;

import java.io.File;

/**
 * @author tendays
 */

public class ThumbnailView extends AppCompatImageView implements ThumbnailIntentService.Callback {

    private static final String TAG = "CloudSpill.thumbs";

    private static final int UNSET = -1;

    private final Activity activity;
    private final Domain domain;
    private int position = UNSET;
    private Domain.Item item = null;

    public ThumbnailView(Activity activity, Domain domain) {
        super(activity);
        this.activity = activity;
        this.domain = domain;

        this.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    public void setPosition(final int position) {
        if (this.position == position) { return; }

        this.position = position;

        this.setImageBitmap(null);
        this.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (item == null) { return; } // TODO Not threadsafe

                final FileBuilder file = item.getFile();
                Log.d(TAG, "Attempting to display "+ file);
                if (file.exists()) {
                    Intent viewIntent = new Intent();
                    viewIntent.setAction(Intent.ACTION_VIEW);
                    // try the java.io.File, as it is more reliable
                    File javaFile = file.getFileEquivalent();

                    Log.d(TAG, "File exists. Java File equivalent: "+ javaFile);
                    if (javaFile != null) {
                        final Uri fileUri = Uri.fromFile(javaFile);
                        Log.d(TAG, "Uri: "+ fileUri);
                        viewIntent.setDataAndType(fileUri, "image/jpeg");
                    } else {
                        viewIntent.setData(file.getUri());
                    }
                    activity.startActivity(viewIntent);
                }
            }
        });

        // Cancel any existing callback registered on the same view (note that callback equality is defined on the bitmap)
        // TODO [NICETOHAVE] Maybe the concept of 'target' should instead be explicit in the thumbnailIntentService API, so it could
        // auto-delete old tasks pointing to the same target
        ThumbnailIntentService.cancelCallback(this);
        ThumbnailIntentService.loadThumbnail(activity, position, this);

    }

    @Override
    public void setItem(Domain.Item item) {
        this.item = item;
    }

    @Override
        public void setThumbnail(final Bitmap bitmap) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ThumbnailView.this.setImageBitmap(bitmap);
                }
            });
    }
}
