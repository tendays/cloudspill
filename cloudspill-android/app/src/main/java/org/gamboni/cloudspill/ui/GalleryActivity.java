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
            Log.d(TAG, "Filling view "+ position);
            ImageView imageView;
            if (convertView == null) {
                imageView = new ImageView(GalleryActivity.this);
                imageView.setLayoutParams((new GridView.LayoutParams(512, 384)));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setPadding(8, 8, 8, 8);
            } else {
                imageView = (ImageView) convertView;
            }

            final int firstVisible = gridView.getFirstVisiblePosition();
            final int lastVisible = gridView.getLastVisiblePosition();
            // "+1" new views are never in the 'visible' portion before they're created
            // 11: temporary to investigate why it doesn't work at all
            if (firstVisible <= position && position <= lastVisible+1 && position < 11) {
                final AbstractDomain.Query<Domain.Item> itemQuery = domain.selectItems();
                final List<Domain.Item> itemList = itemQuery.orderDesc(Domain.Item._DATE).list();
                final FileBuilder file = itemList.get(position).getFile();
                itemQuery.close();
                if (file.exists()) {
                    try {
                        // TODO I could not find documentation for that Thumbnails class
                        //MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), itemList.get(position),
                        //        MediaStore.Images.Thumbnails.MINI_KIND, null);

                        // This works, but seems to keep the entire image in memory: imageView.setImageURI(file.getUri());

                    /* from https://stackoverflow.com/questions/13653526/how-to-find-origid-for-getthumbnail-when-taking-a-picture-with-camera-takepictur */

                        // Thumbnails are 90dp wide. Convert that to the pixel equivalent:
                        final float smallPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());

                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(file.getUri()));
                        // scale the *shortest* dimension to 'smallPx' so (after cropping) the image fills the whole thumbnail
                        float ratio = smallPx / Math.min(bitmap.getWidth(), bitmap.getHeight());

                        bitmap = Bitmap.createScaledBitmap(bitmap,
                                (int) (bitmap.getWidth() * ratio),
                                (int) (bitmap.getHeight() * ratio), false);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 10, baos); //What image format and level of compression to use.
                        imageView.setImageBitmap(bitmap);
                    } catch (FileNotFoundException fnf) {
                        Log.e(TAG, "Could not load file but exists returns true", fnf);
                    }
                }
            } else {
                Log.d(TAG, "Skipped - not between "+ firstVisible +" and "+ lastVisible +"+1");
            }
            return imageView;
        }
    }
}
