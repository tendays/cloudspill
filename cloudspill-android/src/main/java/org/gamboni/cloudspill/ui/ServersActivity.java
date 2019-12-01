package org.gamboni.cloudspill.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;

import java.util.List;

/**
 * @author tendays
 */

public class ServersActivity extends AppCompatActivity implements EditServerFragment.ServerSavedListener {

    private static final String TAG = "CloudSpill.Servers";

    private Domain domain;
    private Domain.Server currentServer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servers);

        ListView lv = (ListView)findViewById(R.id.serverList);

        this.domain = new Domain(this);

        final List<Domain.Server> servers = domain.selectServers();

        lv.setAdapter(new BaseAdapter() {

            @Override
            public int getCount() {
                return servers.size();
            }

            @Override
            public Domain.Server getItem(int i) {
                return servers.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.server_list_item, parent, false);
                }
                Domain.Server server = getItem(position);
                ((TextView) convertView.findViewById(R.id.serverName)).setText(server.get(Domain.ServerSchema.NAME));
                ((TextView) convertView.findViewById(R.id.serverUrl)).setText(server.get(Domain.ServerSchema.URL));
                return convertView;
            }
        });

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                editServer(servers.get(i));
            }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int i, long l) {
                // TODO Ask if server should be deleted
                new AlertDialog.Builder(ServersActivity.this)
                        .setMessage(R.string.confirm_server_delete)
                        .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int wut) {
                                Log.d(TAG, "Deleting server number "+ i);
                                servers.get(i).delete();
                                Log.d(TAG, "Deleted server number "+ i);
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

    /** Add-button listener */
    public void addServer(View view) {
        editServer(null);
    }

    private void editServer(Domain.Server server) {
        currentServer = server;
        new EditServerFragment().show(getFragmentManager(), EditServerFragment.class.getSimpleName());
    }

    @Override
    public Domain getDomain() {
        return this.domain;
    }

    @Override
    public String getServerName() {
        return (currentServer == null) ? "" : currentServer.getName();
    }

    @Override
    public String getServerUrl() {
        return (currentServer == null) ? "" : currentServer.getUrl();
    }

    @Override
    public void onServerSaved(Domain.Server server) {
        if (currentServer != null) {
            server.set(Domain.ServerSchema.ID, currentServer.getId());
        }
        server.insert();
    }
}
