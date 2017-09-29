package org.gamboni.cloudspill.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.support.v7.widget.RecyclerView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;

import org.gamboni.cloudspill.GlideApp;
import org.gamboni.cloudspill.GlideRequests;
import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.job.MediaDownloader;
import org.gamboni.cloudspill.message.StatusReport;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapter responsible for displaying images in the main gallery
 *
 * @author tendays
 */
public class GridViewAdapter extends RecyclerView.Adapter<GridViewAdapter.SimpleViewHolder> implements MediaDownloader.MediaListener {

    private static final String TAG = "CloudSpill."+ GridViewAdapter.class.getSimpleName();

    private final Activity context;
    private final List<Domain.Item> domain;
    /** Maps item serverId to the corresponding view holder. */
    private final Map<Long, SimpleViewHolder> pendingRequests = new HashMap<>();

        public GridViewAdapter(Activity context, Domain domain) {
            this.context = context;
            this.domain = domain.selectItems().like(Domain.Item._PATH, "%.jpg").orderDesc(Domain.Item._DATE).list();
        }

        public static class SimpleViewHolder extends RecyclerView.ViewHolder {
            private final ImageView imageView;
            private GlideRequests target;
            private long serverId = 0;
            public SimpleViewHolder(View view) {
                super(view);
                imageView = (ImageView) view.findViewById(R.id.imageView);
            }
        }

        @Override
        public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Log.d(TAG, "Creating view holder. Parent height: "+ parent.getHeight());
            final View view = LayoutInflater.from(this.context).inflate(R.layout.image_in_grid, parent, false);
            return new SimpleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(SimpleViewHolder holder, final int position) {
            Log.d(TAG, "bind "+ position +". View height: "+ holder.imageView.getHeight());
            Domain.Item item = domain.get(position);
            holder.serverId = item.serverId;
            holder.target = GlideApp.with(context);
            FileBuilder df;
            try {
                df = item.getFile();
            } catch (IllegalArgumentException badUri) { return; }
            if (df.exists()) {
                Log.d(TAG, "Displaying already existing "+ df);
                holder.target.load(df.getUri())
                        .override(1000)
                        .placeholder(R.drawable.lb_ic_in_app_search)
                        .into(holder.imageView);
            } else {
                // File doesn't exist - download it first
                Log.d(TAG, "Item#"+ item.serverId +" not found - issuing download");
                Log.d(TAG, "For: " + df);

                MediaDownloader.setStatusListener(this); // make sure we get readiness notifications
                pendingRequests.put(item.serverId, holder);
                MediaDownloader.download(context, item);
            }

            item.latestAccess = new Date();
            item.update();
        }

        @Override
        public void onViewRecycled(SimpleViewHolder holder) {
            Log.d(TAG, "Clearing "+ holder.getAdapterPosition() +". View height: "+ holder.imageView.getHeight());
            holder.target.clear(holder.imageView);
            pendingRequests.remove(holder.serverId);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return this.domain.size();
        }

    @Override
    public void updateMessage(Severity severity, String message) {
        // TODO display somewhere
    }

    @Override
    public void updatePercent(int percent) {
        /* We don't expect percentage display for media download */
    }

    @Override
    public void mediaReady(long serverId, Uri location) {
        Log.d(TAG, serverId +" ready. Updating view");
        SimpleViewHolder holder = pendingRequests.get(serverId);
        if (holder == null) { return; } // in case the view was recycled already
        holder.target.load(location)
                .override(1000)
                .placeholder(R.drawable.lb_ic_in_app_search)
                .into(holder.imageView);
    }
}
