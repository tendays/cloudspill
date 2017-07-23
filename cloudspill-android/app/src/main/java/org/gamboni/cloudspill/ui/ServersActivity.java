package org.gamboni.cloudspill.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
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

    private Domain domain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servers);

        ListView lv = (ListView)findViewById(R.id.serverList);

        this.domain = new Domain(this);

        lv.setAdapter(new BaseAdapter() {

            List<Domain.Server> servers = domain.selectServers();

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
                ((TextView) convertView.findViewById(R.id.serverName)).setText(server.name);
                ((TextView) convertView.findViewById(R.id.serverUrl)).setText(server.url);
                return convertView;
            }
        });
    }

    /** Add-button listener */
    public void addServer(View view) {
        new EditServerFragment().show(getFragmentManager(), EditServerFragment.class.getSimpleName());
    }

    @Override
    public Domain getDomain() {
        return this.domain;
    }

    @Override
    public void onServerSaved(Domain.Server server) {
        server.insert();
    }
}
