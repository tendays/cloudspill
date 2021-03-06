package org.gamboni.cloudspill.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ortiz.touch.TouchImageView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.EvaluatedFilter;
import org.gamboni.cloudspill.domain.FilterSpecification;
import org.gamboni.cloudspill.domain.HasDomain;
import org.gamboni.cloudspill.graphics.ImageLoader;
import org.gamboni.cloudspill.job.DownloadStatus;
import org.gamboni.cloudspill.job.MediaDownloader;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.domain.Items;

import java.util.List;

/** Activity displaying a single image in full screen.
 *
 * @author tendays
 */
public class ItemActivity extends AppCompatActivity implements HasDomain {

    private static final String TAG = "CloudSpill.Item";

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

        ImageLoader.createLoaderThread();

        FilterSpecification searchFilter = getIntent().<FilterSpecification>getParcelableExtra(FILTER_PARAM);
        if (searchFilter == null) { searchFilter = FilterSpecification.defaultFilter(); }
        this.evaluatedFilter = new EvaluatedFilter(domain, searchFilter);

        setContentView(R.layout.activity_item);

        final ViewPager pager = findViewById(R.id.activity_item_pager);
        pager.setAdapter(new Adapter());

        final Uri uri = getIntent().getData();
        if (uri != null) {
            Log.d(TAG, "Displaying "+ uri);
            int slash = uri.getPath().lastIndexOf('/');
            String file = uri.getPath().substring(slash + 1);
            if (file.endsWith(CloudSpillApi.ID_HTML_SUFFIX)) {
                file = file.substring(0, file.length() - CloudSpillApi.ID_HTML_SUFFIX.length());
            }
            final int serverId = Integer.parseInt(file);

            new Thread() {
                public void run() {
                    // Load the corresponding item
                    List<Domain.Item> items = domain.selectItemsByServerId(serverId);
                    if (items.size() != 1) {
                        // TODO toast
                        Log.e(TAG, "Server id " + serverId + " not found");
                        return;
                    }
                    Domain.Item item = items.get(0);
                    final int position = evaluatedFilter.indexOf(item);
                    if (position == -1) {
                        Log.e(TAG, "Server id "+ serverId +" is excluded by filter");
                        return;
                    }
                    Log.d(TAG, "Found item "+ serverId +" at position "+ position);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pager.setCurrentItem(position);
                        }
                    });
                }
            }.start();
        } else {
            pager.setCurrentItem(getIntent().getIntExtra(POSITION_PARAM, 0));
        }
    }

    @Override
    public Domain getDomain() {
        return this.domain;
    }

    @Override
    protected void onDestroy() {
        ImageLoader.killLoaderThread();

        if (this.evaluatedFilter != null) {
            this.evaluatedFilter.close();
            this.evaluatedFilter = null;
        }
        super.onDestroy();
    }

    private interface MediaCallbackWithErrors extends MediaDownloader.MediaListener, MediaDownloader.OpenListener,
            View.OnClickListener {}

    private class Adapter extends PagerAdapter {
        @Override
        public int getCount() {
            return evaluatedFilter.size();
        }

        @Override
        public View instantiateItem(ViewGroup container, int position) {
            Log.d(TAG, "instantiateItem("+ position +")");
            final FrameLayout frame = new FrameLayout(getBaseContext());
            final View loadingItem = getLayoutInflater().inflate(R.layout.loading_item, null);
            final TextView itemStatus = (TextView)loadingItem.findViewById(R.id.itemStatus);
            final ProgressBar progress = (ProgressBar)loadingItem.findViewById(R.id.itemProgressBar);

            frame.addView(loadingItem, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            container.addView(frame, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

            final Domain.Item item = evaluatedFilter.getByPosition(position);
            if (item.getType() == ItemType.IMAGE) {
                itemStatus.setText("Loading...");
                MediaDownloader.open(ItemActivity.this, item, new MediaCallbackWithErrors() {

                    private final MediaCallbackWithErrors callback = this;

                    @Override
                    public void openItem(final Uri uri, String mime) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                frame.removeAllViews();
                                final TouchImageView imageView = new TouchImageView(frame.getContext());
                                frame.addView(imageView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                                imageView.setImageURI(uri);
                                imageView.setOnClickListener(callback);
                                imageView.setMediaCallback(callback);
                            }
                        });
                    }

                    @Override
                    public void updateCompletion(int percent) {
                        progress.setProgress(percent);
                    }

                    @Override
                    public void mediaReady(Uri location) {
                        // Not called - the MediaDownloader will call openItem instead.
                    }

                    @Override
                    public void notifyStatus(final DownloadStatus status, final String message) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                String suffix = (message == null) ? "" : (": "+ message);
                                if (status == DownloadStatus.DOWNLOAD_FAILED) {
                                    itemStatus.setText("Error downloading item"+ suffix);
                                } else if (status == DownloadStatus.OFFLINE) {
                                    itemStatus.setText("No connection to server" + suffix);
                                } else if (status == DownloadStatus.DECODE_FAILED) {
                                    itemStatus.setText("Unable to open file"+ suffix);
                                } else if (message != null) {
                                    itemStatus.setText(message);
                                } else {
                                    return;
                                }
                                frame.removeAllViews();
                                frame.addView(loadingItem);
                            }
                        });
                    }

                    View overlay;

                    @Override
                    public void onClick(View v) {
                        if (overlay == null) {
                            overlay = getLayoutInflater().inflate(R.layout.item_overlay, null);

                            TextView name = (TextView)overlay.findViewById(R.id.itemOverlayName);
                            name.setText(item.getUser() +"/"+ item.getFolder() +"/"+ item.getPath());

                            TextView tags = (TextView)overlay.findViewById(R.id.itemOverlayTags);
                            StringBuilder tagString = new StringBuilder();
                            String comma = "";
                            for (String tag : item.getTags()) {
                                tagString.append(comma + tag);
                                comma = ", ";
                            }
                            tags.setText(tagString.toString());

                            ImageButton shareButton = (ImageButton)overlay.findViewById(R.id.shareButton);

                            if (item.getChecksum() != null || Items.isPublic(item)) {
                                shareButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        // TODO copy-pasted from share button in ItemFragment
                                        String uri = SettingsActivity.getLastServerVersion(getBaseContext()).getApi()
                                                .getPublicImagePageUrl(item);
                                        Log.d(TAG, "Uri: " + uri);
                                        Intent shareIntent = new Intent();
                                        shareIntent.setAction(Intent.ACTION_SEND);
                                        shareIntent.setType("text/plain");
                                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Sharing URL");
                                        shareIntent.putExtra(Intent.EXTRA_TEXT, uri);
                                        startActivity(Intent.createChooser(shareIntent, "Share via"));
                                    }
                                });
                            } else {
                                shareButton.setVisibility(View.GONE);
                            }

                            ImageButton editButton = (ImageButton)overlay.findViewById(R.id.editButton);
                            editButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    ItemFragment.openItem(ItemActivity.this, item);
                                }
                            });

                            frame.addView(overlay, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                        } else {
                            frame.removeView(overlay);
                            overlay = null;
                        }
                    }
                });
            } else {
                itemStatus.setText("Displaying items of type "+ item.getType() +" is not yet supported");
            }
            return frame;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            final Domain.Item item = evaluatedFilter.getByPosition(position);
            return item.getPath();
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            Log.d(TAG, "destroyItem("+ position +")");
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }
}
