package org.gamboni.cloudspill.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import com.ortiz.touch.TouchImageView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.job.MediaDownloader;

/** Activity displaying a single image in full screen.
 *
 * @author tendays
 */
public class ItemActivity extends AppCompatActivity {

    private static final String ID_PARAM = "item_id";

    private Domain domain;

    public static Intent intent(Context context, long itemId) {
        Intent result = new Intent(context, ItemActivity.class);
        result.putExtra(ID_PARAM, itemId);
        return result;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_item);

        this.domain = new Domain(this);

        long itemId = getIntent().getLongExtra(ID_PARAM, 0);
        if (itemId != 0) {
            final Domain.Item item = domain.selectItems().eq(Domain.ItemSchema.ID, itemId).detachedList().get(0);
            setTitle(item.getPath());
            final TouchImageView imageView = (TouchImageView)findViewById(R.id.activity_item_image);
            MediaDownloader.open(this, item, new MediaDownloader.OpenListener() {
                @Override
                public void openItem(final Uri uri, String mime) {
                    runOnUiThread(new Runnable() {public void run() {
                        imageView.setImageURI(uri);
                    }});
                }

                @Override
                public void updateCompletion(int percent) {
                    // TODO progress bar
                }
            });
        }
    }
}
