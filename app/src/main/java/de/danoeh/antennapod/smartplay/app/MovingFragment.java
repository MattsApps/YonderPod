package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.R;

public class MovingFragment extends Fragment {
    private static final int[][] APPS = {
        {R.string.moving_app_beyondpod, R.string.moving_how_beyondpod},
        {R.string.moving_app_pocket_casts, R.string.moving_how_pocket_casts},
        {R.string.moving_app_overcast, R.string.moving_how_overcast},
        {R.string.moving_app_castbox, R.string.moving_how_castbox},
        {R.string.moving_app_podcast_addict, R.string.moving_how_podcast_addict},
        {R.string.moving_app_antennapod, R.string.moving_how_antennapod},
        {R.string.moving_app_google, R.string.moving_how_google},
    };

    private final ActivityResultLauncher<String[]> pickFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    ImportFlow.open(this, uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.backup_fragment, container, false);
        ((TextView) root.findViewById(R.id.pageTitle)).setText(R.string.moving_title);
        ((TextView) root.findViewById(R.id.pageIntro)).setText(R.string.moving_intro);
        TextView action = root.findViewById(R.id.pageAction);
        action.setText(R.string.moving_pick);
        action.setOnClickListener(v -> pick());
        root.findViewById(R.id.pageActionNote).setVisibility(View.GONE);

        LinearLayout sections = root.findViewById(R.id.pageSections);
        heading(sections, R.string.moving_apps_heading);
        for (int[] app : APPS) {
            View row = inflater.inflate(R.layout.moving_app_row, sections, false);
            ((TextView) row.findViewById(R.id.movingAppName)).setText(app[0]);
            ((TextView) row.findViewById(R.id.movingAppHow)).setText(app[1]);
            row.setOnClickListener(v -> pick());
            sections.addView(row);
        }

        heading(sections, R.string.moving_bulk_heading);
        View bulk = inflater.inflate(R.layout.moving_bulk, sections, false);
        EditText text = bulk.findViewById(R.id.movingBulkText);
        bulk.findViewById(R.id.movingBulkFind).setOnClickListener(v ->
                BulkAddFlow.find(this, text.getText().toString(), () -> text.setText("")));
        sections.addView(bulk);
        View appleRow = inflater.inflate(R.layout.moving_app_row, sections, false);
        ((TextView) appleRow.findViewById(R.id.movingAppName)).setText(R.string.moving_app_apple_spotify);
        ((TextView) appleRow.findViewById(R.id.movingAppHow)).setText(R.string.moving_how_apple_spotify);
        appleRow.setOnClickListener(v -> text.requestFocus());
        sections.addView(appleRow);
        return root;
    }

    private void pick() {
        pickFile.launch(new String[] {"*/*"});
    }

    private void heading(LinearLayout parent, int text) {
        TextView heading = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tune_heading, parent, false);
        heading.setText(text);
        parent.addView(heading);
    }
}
