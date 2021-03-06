package org.gamboni.cloudspill.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;

import java.util.List;

public class FoldersActivity extends AppCompatActivity implements EditFolderFragment.FolderSavedListener {

    private static final String TAG = "CloudSpill.Folders";

    private Domain domain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folders);

        ListView lv = (ListView)findViewById(R.id.folderList);

        this.domain = new Domain(this);

        final List<Domain.Folder> folders = domain.selectFolders();

        BaseAdapter adapter = new BaseAdapter() {


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
                ((TextView) convertView.findViewById(R.id.folderName)).setText(folder.get(Domain.FolderSchema.NAME));
                ((TextView) convertView.findViewById(R.id.folderPath)).setText(folder.get(Domain.FolderSchema.PATH));
                return convertView;
            }
        };

        lv.setAdapter(adapter);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                editFolder(folders.get(i));
            }
        });

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int i, long l) {
                // TODO Ask if folder should be deleted
                new AlertDialog.Builder(FoldersActivity.this)
                        .setMessage(R.string.confirm_folder_delete)
                        .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int wut) {
                                Log.d(TAG, "Deleting folder number "+ i);
                                folders.get(i).delete();
                                Log.d(TAG, "Deleted folder number "+ i);
                            }
                        })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        /* User cancelled, don't do anything */
                    }
                }).show();
                return true;
            }
        });
    }

    private void editFolder(Domain.Folder folder) {
        currentFolder = folder;
        new EditFolderFragment().show(getFragmentManager(), EditFolderFragment.class.getSimpleName());
    }

    /** Add-button listener */
    public void addFolder(View view) {
        editFolder(null);
    }

    Domain.Folder currentFolder = null;

    @Override
    public String getFolderName() {
        return (currentFolder == null) ? "" : currentFolder.get(Domain.FolderSchema.NAME);
    }

    @Override
    public String getFolderPath() {
        return (currentFolder == null) ? "" : currentFolder.get(Domain.FolderSchema.PATH);
    }

    @Override
    public Domain getDomain() {
        return this.domain;
    }

    @Override
    public void onFolderSaved(Domain.Folder folder) {
        if (currentFolder != null) {
            folder.set(Domain.FolderSchema.ID, currentFolder.getId());
        }
        folder.save();
    }
}
