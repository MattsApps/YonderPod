package de.danoeh.antennapod.ui.screen.feed.preferences;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.EditTagsDialogBinding;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.smartplay.app.DownloadSettingsDialog;
import de.danoeh.antennapod.smartplay.app.UpdateScheduler;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.NavDrawerData;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.Keyboard;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

public class TagSettingsDialog extends DialogFragment {
    public static final String TAG = "TagSettingsDialog";
    private static final String ARG_FEED_PREFERENCES = "feed_preferences";
    private static final String ARG_NEW_SUBSCRIPTION = "new_subscription";
    private List<String> displayedTags;
    private EditTagsDialogBinding viewBinding;
    /** Every category: the existing ones, plus any typed here. */
    private List<String> allTags;

    public static TagSettingsDialog newInstanceForNewSubscription(List<FeedPreferences> preferencesList) {
        TagSettingsDialog fragment = newInstance(preferencesList);
        fragment.getArguments().putBoolean(ARG_NEW_SUBSCRIPTION, true);
        return fragment;
    }

    public static TagSettingsDialog newInstance(List<FeedPreferences> preferencesList) {
        TagSettingsDialog fragment = new TagSettingsDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_FEED_PREFERENCES, new ArrayList<>(preferencesList));
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ArrayList<FeedPreferences> feedPreferencesList =
                (ArrayList<FeedPreferences>) getArguments().getSerializable(ARG_FEED_PREFERENCES);
        Set<String> commonTags = new HashSet<>(feedPreferencesList.get(0).getTags());

        for (FeedPreferences preference : feedPreferencesList) {
            commonTags.retainAll(preference.getTags());
        }
        displayedTags = new ArrayList<>(commonTags);
        displayedTags.remove(FeedPreferences.TAG_ROOT);
        allTags = new ArrayList<>(displayedTags);

        viewBinding = EditTagsDialogBinding.inflate(getLayoutInflater());
        viewBinding.rootFolderCheckbox.setChecked(commonTags.contains(FeedPreferences.TAG_ROOT));
        viewBinding.rootFolderCheckbox.setVisibility(UserPreferences.isBottomNavigationEnabled()
                ? View.GONE : View.VISIBLE);

        viewBinding.newTagTextInput.setEndIconOnClickListener(v -> addTypedTag(true));
        viewBinding.newTagEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTypedTag(false);
                return true;
            }
            return false;
        });

        showCategories();
        loadTags();
        keepListAboveKeyboard();

        if (feedPreferencesList.size() > 1) {
            viewBinding.commonTagsInfo.setVisibility(View.VISIBLE);
        }

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setView(viewBinding.getRoot());
        boolean newSubscription = getArguments().getBoolean(ARG_NEW_SUBSCRIPTION, false);
        if (newSubscription && feedPreferencesList.size() == 1) {
            showDownloadRules(feedPreferencesList.get(0).getFeedID());
        }
        dialog.setTitle(newSubscription ? R.string.smartplay_choose_categories : R.string.feed_tags_label);
        dialog.setPositiveButton(R.string.yonder_button_ok, (d, input) -> {
            String typed = viewBinding.newTagEditText.getText() != null
                    ? viewBinding.newTagEditText.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(typed)) {
                choose(typed);
            }
            updatePreferencesTags(feedPreferencesList, commonTags);
        });
        dialog.setNegativeButton(newSubscription ? R.string.smartplay_skip : R.string.cancel_label, null);
        Dialog created = dialog.create();
        // 1.0.7: tapping outside (often to get rid of the keyboard) mustn't throw the choice away.
        created.setCanceledOnTouchOutside(false);
        return created;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            // 1.0.7: no keyboard when the dialog opens (picking an existing category is the usual case); when
            // the new-category box is tapped, the dialog shrinks above the keyboard instead of being covered.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /** One row per category, in name order; a chosen one in the highlight colour with a tick. */
    private void showCategories() {
        if (viewBinding == null || !isAdded()) {
            return;
        }
        Context context = requireContext();
        Collections.sort(allTags, String.CASE_INSENSITIVE_ORDER);
        LinearLayout list = viewBinding.categoriesList;
        list.removeAllViews();
        int primary = MaterialColors.getColor(list, android.R.attr.textColorPrimary);
        int highlight = MaterialColors.getColor(list, R.attr.colorPrimary);
        float density = getResources().getDisplayMetrics().density;
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        for (String tag : allTags) {
            boolean chosen = displayedTags.contains(tag);
            TextView row = new TextView(context);
            row.setText(tag);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.setTextColor(chosen ? highlight : primary);
            row.setMinHeight((int) (48 * density));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (4 * density), 0, (int) (4 * density), 0);
            row.setBackgroundResource(ripple.resourceId);
            if (chosen) {
                Drawable tick = AppCompatResources.getDrawable(context, R.drawable.ic_check);
                if (tick != null) {
                    tick = DrawableCompat.wrap(tick.mutate());
                    DrawableCompat.setTint(tick, highlight);
                }
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, tick, null);
            }
            row.setOnClickListener(v -> {
                if (displayedTags.contains(tag)) {
                    displayedTags.remove(tag);
                } else {
                    displayedTags.add(tag);
                }
                showCategories();
            });
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        viewBinding.categoriesScroll.setVisibility(allTags.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * The list takes at most 40% of the screen space left visible (measured each layout, so it shrinks when
     * the keyboard comes up) and scrolls inside that, keeping the box and the download line in view.
     */
    private void keepListAboveKeyboard() {
        View root = viewBinding.getRoot();
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (viewBinding == null) {
                return;
            }
            Rect visible = new Rect();
            root.getWindowVisibleDisplayFrame(visible);
            int most = (int) (visible.height() * 0.4f);
            int wanted = viewBinding.categoriesList.getHeight() > most ? most : ViewGroup.LayoutParams.WRAP_CONTENT;
            ViewGroup.LayoutParams params = viewBinding.categoriesScroll.getLayoutParams();
            if (params.height != wanted) {
                params.height = wanted;
                viewBinding.categoriesScroll.setLayoutParams(params);
            }
        });
    }

    /** The plus or the keyboard's done: adds and chooses what's typed. */
    private void addTypedTag(boolean fromPlus) {
        String name = viewBinding.newTagEditText.getText() != null
                ? viewBinding.newTagEditText.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name) || FeedPreferences.TAG_UNTAGGED.equals(name)) {
            if (fromPlus) {
                viewBinding.newTagEditText.requestFocus();
                Keyboard.show(getContext(), viewBinding.newTagEditText);
            }
            return;
        }
        choose(name);
        viewBinding.newTagEditText.setText("");
        showCategories();
    }

    /** Chooses a category by name; a name matching an existing one (any capitals) chooses that one. */
    private void choose(String name) {
        if (FeedPreferences.TAG_UNTAGGED.equals(name)) {
            return;
        }
        for (String existing : allTags) {
            if (existing.equalsIgnoreCase(name)) {
                name = existing;
                break;
            }
        }
        if (!allTags.contains(name)) {
            allTags.add(name);
        }
        if (!displayedTags.contains(name)) {
            displayedTags.add(name);
        }
    }

    private void showDownloadRules(long feedId) {
        Context context = requireContext();
        Runnable load = () -> DownloadSettingsDialog.loadSummary(context, feedId, summary -> {
            if (viewBinding != null) {
                viewBinding.downloadRulesLine.setText(context.getString(R.string.follow_download_line, summary));
            }
        });
        load.run();
        viewBinding.downloadRulesLine.setVisibility(View.VISIBLE);
        viewBinding.downloadRulesLine.setOnClickListener(v -> DownloadSettingsDialog.show(context, feedId, load));
    }

    private void loadTags() {
        Observable.fromCallable(
                () -> {
                    NavDrawerData data = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE,
                            Feed.STATE_SUBSCRIBED);
                    List<String> folders = new ArrayList<>();
                    for (NavDrawerData.TagItem item : data.tags) {
                        if (!FeedPreferences.TAG_ROOT.equals(item.getTitle())
                                && !FeedPreferences.TAG_UNTAGGED.equals(item.getTitle())) {
                            folders.add(item.getTitle());
                        }
                    }
                    return folders;
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            for (String tag : result) {
                                if (!allTags.contains(tag)) {
                                    allTags.add(tag);
                                }
                            }
                            showCategories();
                        }, error -> {
                            Log.e(TAG, Log.getStackTraceString(error));
                        });
    }

    private void updatePreferencesTags(List<FeedPreferences> feedPreferencesList, Set<String> commonTags) {
        if (viewBinding.rootFolderCheckbox.isChecked()) {
            displayedTags.add(FeedPreferences.TAG_ROOT);
        }
        List<Future<?>> writes = new ArrayList<>();
        for (FeedPreferences preferences : feedPreferencesList) {
            preferences.getTags().removeAll(commonTags);
            preferences.getTags().addAll(displayedTags);
            writes.add(DBWriter.setFeedPreferences(preferences));
        }
        UpdateScheduler.forgetUnusedCategoriesAfter(requireContext(), writes);
    }
}
