package org.gamboni.cloudspill.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.HasDomain;
import org.gamboni.cloudspill.domain.Splitter;
import org.gamboni.cloudspill.job.DownloadStatus;
import org.gamboni.cloudspill.job.MediaDownloader;
import org.gamboni.cloudspill.job.ThumbnailIntentService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author tendays
 */

public class ItemFragment extends DialogFragment {

    private static final String TAG = "CloudSpill.UI";

    private Domain domain;
    private Domain.Item item;
    private long itemId;

    public static final String ITEM_ID_KEY = "itemId";

    public void setArguments(Bundle arguments) {
        this.itemId = arguments.getLong(ITEM_ID_KEY);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.domain = ((HasDomain)context).getDomain();
        this.item = domain.selectItems().eq(Domain.ItemSchema.ID, itemId).detachedList().get(0);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Pass null as the parent view because its going in the dialog layout
        final View layout = getActivity().getLayoutInflater().inflate(R.layout.fragment_item, null);
        layout.<TextView>findViewById(R.id.itemAuthor).setText(getString(R.string.item_by, item.getUser()));
        layout.<TextView>findViewById(R.id.itemDate).setText("Created on "+ DateFormat.getDateFormat(getActivity()).format(item.getDate()));
        StringBuilder tagString = new StringBuilder();
        String comma = "";
        for (String tag : item.getTags()) {
            tagString.append(comma).append(tag);
            comma = ",";
        }
        final EditText tagBox = layout.findViewById(R.id.itemTags);
        tagBox.setText(tagString.toString());

        ThumbnailIntentService.Callback callback = new ThumbnailIntentService.Callback() {
            @Override
            public void setItem(Domain.Item item) {
                // Nothing to do, we have it already
            }

            @Override
            public void setThumbnail(final Bitmap bitmap) {
                final Activity activity = getActivity();
                if (activity == null) { return; }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        layout.<ImageView>findViewById(R.id.itemImage).setImageBitmap(bitmap);
                    }
                });
            }

            @Override
            public void setStatus(DownloadStatus status) {
                // TODO display message
            }
        };

        ThumbnailIntentService.cancelCallback(callback);
        ThumbnailIntentService.loadThumbnailForId(getActivity(), itemId, callback);

        return new AlertDialog.Builder(getActivity())
                .setTitle(item.getUser() +"/"+ item.getFolder() +"/"+ item.getPath() +
                        "("+ item.getType().name().toLowerCase() +")")
                .setView(layout)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        final Collection<String> newTags = new Splitter(tagBox.getText().toString(), ',').trimValues().allRemainingTo(new ArrayList<String>());
                        Iterator<String> oldTags = item.getTags().iterator();
                        while (oldTags.hasNext()) {
                            String tag = oldTags.next();
                            if (!newTags.contains(tag)) {
                                oldTags.remove();
                            }
                        }
                        for (String tag : newTags) {
                            if (!item.getTags().contains(tag)) {
                                item.getTags().add(tag);
                            }
                        }
                    }
                })
                .setNeutralButton(R.string.share, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri.parse(SettingsActivity.getLastServerVersion(getActivity()).getPublicUrl() +"/item/http/"+ item.getServerId() +"?key="+ item.get(
                                Domain.ItemSchema.CHECKSUM));
                        Log.d(TAG, "Uri: "+ uri);
                        Intent shareIntent = new Intent();
                        shareIntent.setAction(Intent.ACTION_SEND);
                        shareIntent.setData(uri);
                        startActivity(Intent.createChooser(shareIntent, "Share via"));
                    }
                })
                .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .create();
    }

}
