package org.gamboni.cloudspill.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.job.ThumbnailIntentService;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

/** Experimental gallery view based on a fixed-size grid.
 *
 * @author tendays
 */
public class GalleryActivity extends AppCompatActivity {

    private static final String TAG = "CloudSpill.Gallery";
    private Domain domain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        this.domain = new Domain(GalleryActivity.this);

        GridView gridView = (GridView) findViewById(R.id.gallery_grid);
        GalleryAdapter adapter = new GalleryAdapter(gridView);
        gridView.setAdapter(adapter);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }

    @Override
    protected void onDestroy() {
        domain.close();
        domain = null;
        super.onDestroy();
    }

    private class GalleryAdapter extends BaseAdapter {
        GridView gridView;

        GalleryAdapter(GridView gridView) {
            this.gridView = gridView;
        }

        @Override
        public int getCount() {
            return 100; // TODO can we have an 'infinite' count?
        }

        @Override
        public Object getItem(int position) {
            return null; // TODO What is this for?
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Log.d(TAG, "Filling view " + position);
            final ImageView imageView;
            if (convertView == null) {
                imageView = new ImageView(GalleryActivity.this);
                imageView.setLayoutParams((new GridView.LayoutParams(512, 384)));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setPadding(8, 8, 8, 8);
            } else {
                imageView = (ImageView) convertView;
            }

            imageView.setImageBitmap(null);

//            final int firstVisible = gridView.getFirstVisiblePosition();
//            final int lastVisible = gridView.getLastVisiblePosition();

            // TODO cancel callback when the view is closed
            ThumbnailIntentService.loadThumbnail(GalleryActivity.this, position, new ThumbnailIntentService.Callback() {
                @Override
                public void setThumbnail(final Bitmap bitmap) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(bitmap);
                        }
                    });
                }
            });
            return imageView;
        }
    }
}
