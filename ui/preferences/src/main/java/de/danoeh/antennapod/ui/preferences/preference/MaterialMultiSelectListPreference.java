package de.danoeh.antennapod.ui.preferences.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import androidx.preference.MultiSelectListPreference;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.ui.preferences.R;

import java.util.HashSet;
import java.util.Set;

public class MaterialMultiSelectListPreference extends MultiSelectListPreference {

    public MaterialMultiSelectListPreference(Context context) {
        super(context);
    }

    public MaterialMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getTitle());
        builder.setIcon(getDialogIcon());
        builder.setNegativeButton(R.string.cancel_label, null);

        boolean[] selected = new boolean[getEntries().length];
        CharSequence[] values = getEntryValues();
        for (int i = 0; i < values.length; i++) {
            selected[i] = getValues().contains(values[i].toString());
        }
        builder.setMultiChoiceItems(getEntries(), selected,
                (DialogInterface dialog, int which, boolean isChecked) -> selected[which] = isChecked);
        builder.setPositiveButton(R.string.yonder_button_ok, (dialog, which) -> {
            Set<String> selectedValues = new HashSet<>();
            for (int i = 0; i < values.length; i++) {
                if (selected[i]) {
                    selectedValues.add(getEntryValues()[i].toString());
                }
            }
            setValues(selectedValues);
        });
        builder.show();
    }
}
