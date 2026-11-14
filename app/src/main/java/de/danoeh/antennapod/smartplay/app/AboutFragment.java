package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.IntentUtils;
import de.danoeh.antennapod.ui.preferences.screen.about.LicensesFragment;

public class AboutFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.backup_fragment, container, false);
        ((TextView) root.findViewById(R.id.pageTitle)).setText(R.string.yonder_wordmark);
        ((TextView) root.findViewById(R.id.pageIntro)).setText(getString(R.string.about_intro,
                BuildConfig.VERSION_NAME));
        TextView action = root.findViewById(R.id.pageAction);
        action.setText(R.string.about_feedback);
        action.setOnClickListener(v -> askFeedback());
        root.findViewById(R.id.pageActionNote).setVisibility(View.GONE);
        addTipButton(action);

        LinearLayout sections = root.findViewById(R.id.pageSections);
        row(sections, R.string.about_made_by, getString(R.string.yonder_developer_name), this::askFeedback);
        heading(sections, R.string.about_thanks_heading);
        note(sections, getString(R.string.about_thanks));
        row(sections, R.string.about_antennapod, getString(R.string.about_antennapod_value), () ->
                IntentUtils.openInBrowser(requireContext(), "https://github.com/AntennaPod/AntennaPod"));

        heading(sections, R.string.about_open_heading);
        row(sections, R.string.about_licence, getString(R.string.about_licence_value), this::showLicence);
        row(sections, R.string.about_source, getString(R.string.about_source_value), () ->
                IntentUtils.openInBrowser(requireContext(), getString(R.string.yonder_source_url)));
        row(sections, R.string.about_libraries, "", () -> TendRows.open(this, new LicensesFragment()));
        row(sections, R.string.about_privacy, "", () ->
                IntentUtils.openInBrowser(requireContext(), getString(R.string.yonder_privacy_url)));
        return root;
    }

    /**
     * "tip me", on both builds from day one. Play: the same tip choices as the top "tip me". F-Droid: the
     * web page in yonder_tip_link (nothing opens while that link is empty).
     */
    private void addTipButton(TextView feedback) {
        ViewGroup parent = (ViewGroup) feedback.getParent();
        TextView tip = new TextView(requireContext(), null, 0, R.style.Widget_WildInk_Block);
        tip.setText(R.string.tip_me_mark);
        tip.setGravity(Gravity.CENTER);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tip.setContentDescription(getString(R.string.tip_me_description));
        tip.setOnClickListener(v -> TipShop.open(this));
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, feedback.getLayoutParams().height);
        params.topMargin = Math.round(12 * density);
        parent.addView(tip, parent.indexOfChild(feedback) + 1, params);
    }

    private void askFeedback() {
        CheckBox attachLog = new CheckBox(requireContext());
        attachLog.setText(R.string.feedback_attach_log);
        attachLog.setChecked(true);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout holder = new LinearLayout(requireContext());
        holder.setPadding(padding, padding / 2, padding, 0);
        holder.addView(attachLog);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_feedback)
                .setMessage(R.string.feedback_explain)
                .setView(holder)
                .setPositiveButton(R.string.feedback_write, (dialog, which) ->
                        Feedback.send(requireActivity(), attachLog.isChecked(), false))
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .show();
    }

    private void showLicence() {
        String text;
        try (InputStream in = requireContext().getAssets().open("LICENSE.txt")) {
            text = IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            text = getString(R.string.about_licence_value);
        }
        TextView body = new TextView(requireContext());
        TextViewCompat.setTextAppearance(body, R.style.TextAppearance_WildInk_Detail);
        body.setTextIsSelectable(true);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding / 2, padding, padding);
        body.setText(text);
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(body);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_licence_value)
                .setView(scroll)
                .setPositiveButton(R.string.yonder_button_ok, null)
                .show();
    }

    private void heading(LinearLayout parent, int text) {
        TextView heading = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tune_heading, parent, false);
        heading.setText(text);
        parent.addView(heading);
    }

    private void note(LinearLayout parent, String text) {
        TextView note = new TextView(parent.getContext());
        TextViewCompat.setTextAppearance(note, R.style.TextAppearance_WildInk_Detail);
        note.setText(text);
        note.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, 0);
        parent.addView(note);
    }

    private void row(LinearLayout parent, int label, String value, Runnable onClick) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.tune_row, parent, false);
        ((TextView) row.findViewById(R.id.tuneRowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tuneRowValue)).setText(value);
        row.setOnClickListener(v -> onClick.run());
        parent.addView(row);
    }
}
