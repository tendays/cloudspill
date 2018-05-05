package org.gamboni.cloudspill.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.ortiz.touch.ExtendedViewPager;
import com.ortiz.touch.TouchImageView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.EvaluatedFilter;
import org.gamboni.cloudspill.domain.FilterSpecification;
import org.gamboni.cloudspill.domain.ItemType;
import org.gamboni.cloudspill.job.MediaDownloader;

/** Activity displaying a single image in full screen.
 *
 * @author tendays
 */
public class ItemActivity extends AppCompatActivity {

    private static final String POSITION_PARAM = "org.gamboni.cloudspill.param.position";
    private static final String FILTER_PARAM = "org.gamboni.cloudspill.param.filter";

    private Domain domain;
    private EvaluatedFilter evaluatedFilter;

    public static Intent intent(Context context, int position, FilterSpecification filter) {
        Intent result = new Intent(context, ItemActivity.class);
        result.putExtra(POSITION_PARAM, position);
        result.putExtra(FILTER_PARAM, filter);
        return result;
    }

    public ItemActivity() {
        this.domain = new Domain(this);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.evaluatedFilter = new EvaluatedFilter(domain, getIntent().<FilterSpecification>getParcelableExtra(FILTER_PARAM));

        setContentView(R.layout.activity_item);

        final ExtendedViewPager pager = (ExtendedViewPager) findViewById(R.id.activity_item_pager);
        pager.setAdapter(new Adapter());
        pager.setCurrentItem(getIntent().getIntExtra(POSITION_PARAM, 0));
    }

    @Override
    protected void onDestroy() {
        if (this.evaluatedFilter != null) {
            this.evaluatedFilter.close();
            this.evaluatedFilter = null;
        }
        super.onDestroy();
    }

    private class Adapter extends PagerAdapter {
        @Override
        public int getCount() {
            return evaluatedFilter.size();
        }

        @Override
        public View instantiateItem(ViewGroup container, int position) {
            final TouchImageView imageView = new TouchImageView(container.getContext());
            final Domain.Item item = evaluatedFilter.getByPosition(position);
            if (item.getType() == ItemType.IMAGE) {
                MediaDownloader.open(ItemActivity.this, item, new MediaDownloader.OpenListener() {
                    @Override
                    public void openItem(final Uri uri, String mime) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                imageView.setImageURI(uri);
                            }
                        });
                    }

                    @Override
                    public void updateCompletion(int percent) {
                        // TODO progress bar
                    }
                });
            }
            container.addView(imageView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            return imageView;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            final Domain.Item item = evaluatedFilter.getByPosition(position);
            return item.getPath();
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }
}
