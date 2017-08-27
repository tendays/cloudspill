package org.gamboni.cloudspill.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;

/**
 * @author tendays
 */
public class EditFolderFragment extends DialogFragment {

    private static final String TAG = "CloudSpill.Folder";

    public interface FolderSavedListener {
        Domain getDomain();
        String getFolderName();
        String getFolderPath();
        void onFolderSaved(Domain.Folder folder);
    }

    private Domain domain;
    private Button pathField;
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
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // https://stackoverflow.com/questions/40197375/storage-access-framework-not-showing-external-storage-device
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        /*intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("* /*");*/
        ((Button)layout.findViewById(R.id.editFolderPath)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(intent , 42);
            }
        });
        final TextView nameField = layout.findViewById(R.id.editFolderName);
        this.pathField = layout.findViewById(R.id.editFolderPath);

        nameField.setText(listener.getFolderName());
        pathField.setText(listener.getFolderPath());

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode != Activity.RESULT_OK)
            return;
        Uri treeUri = resultData.getData();
        //DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
        Log.d(TAG, "treeUri="+ treeUri);
        // sd-card example uri:
        // content://com.android.externalstorage.documents/tree/3563-3866%3ADCIM

        // internal storage example uri:
        // content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera
        Log.d(TAG, "treeUri Path = "+ treeUri.getPath());
        getActivity().grantUriPermission(getActivity().getPackageName(), treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getActivity().getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        this.pathField.setText(treeUri.toString());
    }
}
