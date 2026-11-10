package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.Keyboard;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HelpFragment extends Fragment {
    private static final String TAG = "HelpFragment";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Set<String> openGroups = new HashSet<>();
    private LinearLayout list;
    private EditText query;
    @Nullable
    private HelpIndex index;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.help_fragment, container, false);
        list = root.findViewById(R.id.helpList);
        query = root.findViewById(R.id.helpQuery);
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                show();
            }
        });
        query.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && getActivity() != null) {
                Keyboard.hide(getActivity());
                return true;
            }
            return false;
        });
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> HelpIndex.load(app))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loaded -> {
                    index = loaded;
                    show();
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void show() {
        if (index == null || list == null || getContext() == null) {
            return;
        }
        list.removeAllViews();
        String text = query.getText().toString().trim();
        if (text.isEmpty()) {
            browse();
            return;
        }
        List<HelpIndex.Item> found = index.search(text);
        if (found.isEmpty()) {
            note(R.string.help_no_results);
            return;
        }
        for (HelpIndex.Item item : found) {
            list.addView(row(item.title, item.path, 0, v -> open(item)));
        }
    }

    private void browse() {
        note(R.string.help_try);
        TextView heading = (TextView) LayoutInflater.from(list.getContext())
                .inflate(R.layout.tune_heading, list, false);
        heading.setText(R.string.help_topics);
        list.addView(heading);
        int indent = (int) (16 * getResources().getDisplayMetrics().density);
        for (HelpIndex.Group group : index.groups) {
            View row = LayoutInflater.from(list.getContext()).inflate(R.layout.tune_row, list, false);
            ((TextView) row.findViewById(R.id.tuneRowLabel)).setText(group.name);
            row.setOnClickListener(v -> {
                if (!openGroups.remove(group.name)) {
                    openGroups.add(group.name);
                }
                show();
            });
            list.addView(row);
            if (openGroups.contains(group.name)) {
                for (HelpIndex.Item topic : group.topics) {
                    list.addView(row(topic.title, topic.about, indent, v -> open(topic)));
                }
            }
        }
    }

    private void note(int text) {
        TextView note = new TextView(list.getContext());
        TextViewCompat.setTextAppearance(note, R.style.TextAppearance_WildInk_Detail);
        note.setText(text);
        note.setPadding(0, (int) (16 * getResources().getDisplayMetrics().density), 0, 0);
        list.addView(note);
    }

    private View row(String title, @Nullable String detail, int indent, View.OnClickListener onClick) {
        View row = LayoutInflater.from(list.getContext()).inflate(R.layout.help_result_row, list, false);
        ((TextView) row.findViewById(R.id.helpResultTitle)).setText(title);
        TextView detailView = row.findViewById(R.id.helpResultPath);
        detailView.setText(detail);
        detailView.setVisibility(detail == null || detail.isEmpty() ? View.GONE : View.VISIBLE);
        row.setPaddingRelative(indent, row.getPaddingTop(), 0, row.getPaddingBottom());
        row.setOnClickListener(onClick);
        return row;
    }

    private void open(HelpIndex.Item item) {
        if (getActivity() != null) {
            Keyboard.hide(getActivity());
        }
        switch (item.open) {
            case HelpIndex.OPEN_TUNE:
                openSheet(new TuneFragment(), item.target);
                break;
            case HelpIndex.OPEN_CONTROLS:
                openSheet(new ControlsSettingsFragment(), item.target);
                break;
            case HelpIndex.OPEN_SLEEP:
                openSheet(new SleepSettingsFragment(), item.target);
                break;
            case HelpIndex.OPEN_SCHEDULE:
                openSheet(new UpdateScheduleFragment(), item.target);
                break;
            case HelpIndex.OPEN_BACKUP:
                openSheet(new BackupFragment(), item.target);
                break;
            case HelpIndex.OPEN_MOVING:
                openSheet(new MovingFragment(), item.target);
                break;
            case HelpIndex.OPEN_ABOUT:
                openSheet(new AboutFragment(), item.target);
                break;
            case HelpIndex.OPEN_FIND_PODCAST:
                openSheet(new FindPodcastsFragment(), null);
                break;
            case HelpIndex.OPEN_FIND_BOOK:
                openSheet(LibriVoxSearchFragment.newInstance(null), null);
                break;
            case HelpIndex.OPEN_FIND_STATION:
                openSheet(new RadioSearchFragment(), null);
                break;
            case HelpIndex.OPEN_WELCOME:
                WelcomeActivity.show(requireActivity());
                break;
            case HelpIndex.OPEN_ADVANCED:
                Intent intent = new Intent(requireContext(), PreferenceActivity.class);
                intent.putExtra(PreferenceActivity.OPEN_SCREEN, item.screen);
                intent.putExtra(PreferenceActivity.HIGHLIGHT_KEY, item.target);
                startActivity(intent);
                break;
            case HelpIndex.OPEN_TOPIC:
            default:
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(item.title)
                        .setMessage(item.about)
                        .setPositiveButton(R.string.yonder_button_ok, null)
                        .show();
                break;
        }
    }

    private void openSheet(Fragment fragment, @Nullable String highlight) {
        SettingHighlight.text(fragment, highlight);
        TendRows.open(this, fragment);
    }
}
