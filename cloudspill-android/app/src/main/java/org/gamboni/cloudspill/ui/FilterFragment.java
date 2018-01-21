package org.gamboni.cloudspill.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;

/**
 * @author tendays
 */

public class FilterFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Pass null as the parent view because its going in the dialog layout
        final View layout = getActivity().getLayoutInflater().inflate(R.layout.edit_filter, null);
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.edit_filter_title)
                .setView(layout)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
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
