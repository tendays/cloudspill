package org.gamboni.cloudspill.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import org.gamboni.cloudspill.job.MediaDownloader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tendays on 28.06.17.
 */

public class GridViewAdapter extends RecyclerView.Adapter<GridViewAdapter.SimpleViewHolder>{

    private static final String TAG = "CloudSpill."+ GridViewAdapter.class.getSimpleName();

    private final Context context;
    private final List<Domain.Item> domain;

        public GridViewAdapter(Context context, Domain domain) {
            this.context = context;
            this.domain = domain.selectItems().orderDesc(Domain.Item._DATE).list();
        }

        public static class SimpleViewHolder extends RecyclerView.ViewHolder {
            private final ImageView imageView;
            private GlideRequests target;
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

            holder.target = GlideApp.with(context);
            if (item.getFile().target.exists()) {
                holder.target.load(Uri.fromFile(item.getFile().target))
                        .override(1000)
                        .placeholder(R.drawable.lb_ic_in_app_search)
                        .into(holder.imageView);
            } else {
                // File doesn't exist - download it first
                MediaDownloader.download(context, item.serverId, item.getFile());
            }
        }

        @Override
        public void onViewRecycled(SimpleViewHolder holder) {
            Log.d(TAG, "Clearing "+ holder.getAdapterPosition() +". View height: "+ holder.imageView.getHeight());
            holder.target.clear(holder.imageView);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return this.domain.size();
        }


}
