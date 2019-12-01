package org.gamboni.cloudspill.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;

/**
 * @author tendays
 */
public class EditServerFragment extends DialogFragment {

    public interface ServerSavedListener {
        Domain getDomain();
        String getServerName();
        String getServerUrl();
        void onServerSaved(Domain.Server server);
    }

    private Domain domain;

    private ServerSavedListener listener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        this.listener = (ServerSavedListener) context;
        this.domain = listener.getDomain();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Pass null as the parent view because its going in the dialog layout
        final View layout = getActivity().getLayoutInflater().inflate(R.layout.edit_server, null);
        final TextView nameField = ((TextView)layout.findViewById(R.id.editServerName));
        nameField.setText(listener.getServerName());
        final TextView urlField = ((TextView)layout.findViewById(R.id.editServerUrl));
        urlField.setText(listener.getServerUrl());
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.edit_server_title)
                .setView(layout)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Domain.Server server = domain.new Server();
                        server.set(Domain.ServerSchema.NAME, nameField.getText().toString());
                        server.set(Domain.ServerSchema.URL, urlField.getText().toString());
                        listener.onServerSaved(server);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // edition cancelled: nothing to do
                    }
                })
                .create();
    }
}
