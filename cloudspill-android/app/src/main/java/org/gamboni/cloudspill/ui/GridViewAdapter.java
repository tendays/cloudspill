package org.gamboni.cloudspill.ui;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.support.v7.widget.RecyclerView;
import android.widget.Toast;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tendays on 28.06.17.
 */

public class GridViewAdapter extends RecyclerView.Adapter<GridViewAdapter.SimpleViewHolder>{

        private final Context context;
    private final List<Domain.Item> domain;
    private final File root;

        public GridViewAdapter(Context context, File root, Domain domain) {
            this.context = context;
            this.root = root;
            this.domain = domain.selectItems();
        }

        public static class SimpleViewHolder extends RecyclerView.ViewHolder {
            public final ImageView imageView;
            public SimpleViewHolder(View view) {
                super(view);
                imageView = (ImageView) view.findViewById(R.id.imageView);
            }
        }
 
        @Override
        public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(this.context).inflate(R.layout.image_in_grid, parent, false);
            return new SimpleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(SimpleViewHolder holder, final int position) {
            Domain.Item item = domain.get(position);
            holder.imageView.setImageURI(Uri.fromFile(new File(root, item.path)));
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
