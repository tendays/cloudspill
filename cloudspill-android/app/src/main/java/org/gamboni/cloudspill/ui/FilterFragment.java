package org.gamboni.cloudspill.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.FilterSpecification;
import org.gamboni.cloudspill.domain.HasDomain;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

/**
 * @author tendays
 */

public class FilterFragment extends DialogFragment {

    private static final String TAG = "Cloudspill.Filter";
    private FilterSpecification currentFilter;
    private FilterListener listener;
    private Domain domain;

    private enum CurrentDatePicker {
        FROM {
             Date getCurrentDate(FilterSpecification currentFilter) {
                return currentFilter.from;
            }

             FilterSpecification updateFilter(FilterSpecification current, Date date) {
                return new FilterSpecification(date, current.to, current.by, current.sort);
            }
        },
        TO {
             Date getCurrentDate(FilterSpecification currentFilter) {
                return currentFilter.to;
            }

            FilterSpecification updateFilter(FilterSpecification current, Date date) {
                return new FilterSpecification(current.from, date, current.by, current.sort);
            }
        };

        abstract Date getCurrentDate(FilterSpecification currentFilter);

        abstract FilterSpecification updateFilter(FilterSpecification current, Date date);
    }

    public interface FilterListener extends HasDomain {
        /** Return the current filter. */
        FilterSpecification getFilter();
        void filterChanged(FilterSpecification filter);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        this.listener = (FilterListener) context;
        this.domain = listener.getDomain();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Pass null as the parent view because its going in the dialog layout
        final View layout = getActivity().getLayoutInflater().inflate(R.layout.edit_filter, null);

        /* Fill 'by' spinner values. */
        final Spinner byEdit = layout.findViewById(R.id.byEdit);
        List<String> items = domain.selectItems().selectDistinct(Domain.ItemSchema.USER).detachedList();
        byEdit.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, items));

        this.currentFilter = listener.getFilter();
        final java.text.DateFormat dateFormat = DateFormat.getDateFormat(getActivity());

        class DateFilterSetup implements CompoundButton.OnCheckedChangeListener, DatePickerFragment.DateListener,
                View.OnClickListener {

            final CurrentDatePicker field;
            final Button button;
            final CheckBox checkbox;

            DateFilterSetup(int checkRes, int buttonRes, final CurrentDatePicker field) {
                this.field = field;

                this.checkbox = layout.findViewById(checkRes);
                checkbox.setOnCheckedChangeListener(this);

                this.button = layout.findViewById(buttonRes);
                button.setOnClickListener(this);

                updateButtonText();
            }

            private void updateButtonText() {
                Date value = getCurrentDate();
                button.setText(
                        (value == null) ? getResources().getText(R.string.filter_date_btn) :
                                dateFormat.format(currentFilter.from));

                checkbox.setChecked(value != null);
            }

            @Override
            public void onClick(View v) {
                if (checkbox.isChecked()) {
                    openDatePicker();
                } else {
                    checkbox.setChecked(true);
                }
            }

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    openDatePicker();
                } else {
                    onDateSet(null);
                }
            }

            @Nullable
            @Override
            public Date getCurrentDate() {
                return field.getCurrentDate(currentFilter);
            }

            @Override
            public void onDateSet(Date date) {
                Log.d(TAG, "onDateSet("+ date +")");
                currentFilter = field.updateFilter(currentFilter, date);
                updateButtonText();
            }

            private void openDatePicker() {
                final DatePickerFragment picker = new DatePickerFragment();
                picker.setListener(this);
                picker.show(getFragmentManager(), DatePickerFragment.class.getSimpleName());
            }
        }

        new DateFilterSetup(R.id.fromCheck, R.id.fromEdit, CurrentDatePicker.FROM);
        new DateFilterSetup(R.id.toCheck, R.id.toEdit, CurrentDatePicker.TO);
        layout.<CheckBox>findViewById(R.id.byCheck).setChecked(currentFilter.by != null);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.edit_filter_title)
                .setView(layout)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        listener.filterChanged(new FilterSpecification(
                                FilterFragment.this.currentFilter.from,
                                FilterFragment.this.currentFilter.to,
                                layout.<CheckBox>findViewById(R.id.byCheck).isChecked() ?
                                        (String)((Spinner)layout.findViewById(R.id.byEdit)).getSelectedItem() :
                                        null,
                                select(FilterSpecification.Sort.values(), R.id.filter_sort)));
                    }

                    private <T> T select(T[] array, int resource) {
                        int index = layout.<Spinner>findViewById(resource)
                                .getSelectedItemPosition();
                        return (index == AdapterView.INVALID_POSITION) ? null : array[index];
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
