package org.gamboni.cloudspill.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.HasDomain;

/**
 * @author tendays
 */

public class ItemFragment extends DialogFragment {

    private Domain domain;
    private Domain.Item item;
    private long itemId;

    public static final String ITEM_ID_KEY = "itemId";

    public void setArguments(Bundle arguments) {
        this.itemId = arguments.getLong(ITEM_ID_KEY);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.domain = ((HasDomain)context).getDomain();
        this.item = domain.selectItems().eq(Domain.Item._ID, itemId).detachedList().get(0);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Pass null as the parent view because its going in the dialog layout
        final View layout = getActivity().getLayoutInflater().inflate(R.layout.fragment_item, null);
        layout.<TextView>findViewById(R.id.itemAuthor).setText(item.user);
        layout.<TextView>findViewById(R.id.itemDate).setText(DateFormat.getDateFormat(getActivity()).format(item.date));

        return new AlertDialog.Builder(getActivity())
                .setTitle(item.user +"/"+ item.folder +"/"+ item.path)
                .setView(layout)
                .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .create();
    }

}
