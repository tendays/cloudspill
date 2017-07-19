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
public class EditFolderFragment extends DialogFragment {

    public interface FolderSavedListener {
        Domain getDomain();
        void onFolderSaved(Domain.Folder folder);
    }

    private Domain domain;

    private FolderSavedListener listener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        this.listener = (FolderSavedListener) context;
        this.domain = listener.getDomain();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Pass null as the parent view because its going in the dialog layout
        final View layout = getActivity().getLayoutInflater().inflate(R.layout.edit_folder, null);
        final TextView nameField = ((TextView)layout.findViewById(R.id.editFolderName));
        final TextView pathField = ((TextView)layout.findViewById(R.id.editFolderPath));
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.edit_folder_title)
                .setView(layout)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Domain.Folder folder = domain.new Folder();
                        folder.name = nameField.getText().toString();
                        folder.path = pathField.getText().toString();
                        listener.onFolderSaved(folder);
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
