package org.gamboni.cloudspill.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.DatePicker;

import java.util.Calendar;
import java.util.Date;

/**
 * @author tendays
 */

public class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

        public interface DateListener {
            /** Return the selected date, or null if none is currently selected. */
            @Nullable
            Date getCurrentDate();
            void onDateSet(Date date);
        }

    DateListener listener = null;

    public void setListener(DateListener listener) {
        this.listener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Date preSelected = listener.getCurrentDate();

        // Use the current date as the default values for the picker
        final Calendar c = Calendar.getInstance();
        if (preSelected != null) {
            c.setTime(preSelected);
        }

        return new DatePickerDialog(getActivity(), this,
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        listener.onDateSet(new Date(year-1900, month, dayOfMonth));
    }


}
