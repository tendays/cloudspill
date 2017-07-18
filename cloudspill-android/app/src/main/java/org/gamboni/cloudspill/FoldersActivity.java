package org.gamboni.cloudspill;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.gamboni.cloudspill.domain.Domain;

import java.util.List;

public class FoldersActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folders);

        ListView lv = (ListView)findViewById(R.id.folderList);

        final Domain domain = new Domain(this);

        lv.setAdapter(new BaseAdapter() {

            List<Domain.Folder> folders = domain.selectFolders();

            @Override
            public int getCount() {
                return folders.size();
            }

            @Override
            public Domain.Folder getItem(int i) {
                return folders.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.folder_list_item, parent, false);
                }
                Domain.Folder folder = getItem(position);
                ((TextView) convertView.findViewById(R.id.folderName)).setText(folder.name);
                ((TextView) convertView.findViewById(R.id.folderPath)).setText(folder.path);
                return convertView;
            }
        });
    }

    public void addFolder(View view) {
        // TODO
    }
}
