package de.danoeh.antennapod.smartplay.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Date;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.smartplay.support.FirstRun;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.system.CrashReportWriter;
import de.danoeh.antennapod.ui.common.ThemeSwitcher;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class WelcomeActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_MOVING = "openMoving";
    private static final String PREFS = "YonderWelcome";
    private static final String KEY_SHOWN = "shown";
    private static final String KEY_CRASH_ANSWERED = "crashAnswered";
    private static final String KEY_CRASH_NEVER = "crashNever";
    private static final int PAGES = 4;

    private ViewPager2 pager;
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> refreshPage());

    public static void show(Activity activity) {
        activity.startActivity(new Intent(activity, WelcomeActivity.class));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void maybeShow(Activity activity) {
        Context app = activity.getApplicationContext();
        Single.fromCallable(() -> {
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() == Feed.STATE_SUBSCRIBED && !Radio.isRadioFeed(feed)) {
                    return true;
                }
            }
            return false;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(hasShows -> {
                    if (activity.isFinishing()) {
                        return;
                    }
                    if (FirstRun.showWelcome(prefs(app).getBoolean(KEY_SHOWN, false), hasShows)) {
                        prefs(app).edit().putBoolean(KEY_SHOWN, true).apply();
                        show(activity);
                    } else {
                        if (!hasShows) {
                            prefs(app).edit().putBoolean(KEY_SHOWN, true).apply();
                        }
                        maybeAskAboutCrash(activity);
                    }
                }, error -> maybeAskAboutCrash(activity));
    }

    private static void maybeAskAboutCrash(Activity activity) {
        Date crash = CrashReportWriter.getTimestamp();
        long crashTime = crash != null ? crash.getTime() : 0;
        SharedPreferences prefs = prefs(activity);
        if (!FirstRun.askAboutCrash(crashTime, prefs.getLong(KEY_CRASH_ANSWERED, 0),
                prefs.getBoolean(KEY_CRASH_NEVER, false))) {
            return;
        }
        Runnable answered = () -> prefs.edit().putLong(KEY_CRASH_ANSWERED, crashTime).apply();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.crash_prompt_title)
                .setMessage(R.string.crash_prompt_message)
                .setPositiveButton(R.string.crash_prompt_send, (dialog, which) -> {
                    answered.run();
                    Feedback.send(activity, true, true);
                })
                .setNegativeButton(R.string.crash_prompt_later, (dialog, which) -> answered.run())
                .setNeutralButton(R.string.crash_prompt_never, (dialog, which) -> {
                    answered.run();
                    prefs.edit().putBoolean(KEY_CRASH_NEVER, true).apply();
                })
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeSwitcher.getNoTitleTheme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_activity);
        pager = findViewById(R.id.welcomePager);
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.welcome_page, parent, false)) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                bindPage(holder.itemView, position);
            }

            @Override
            public int getItemCount() {
                return PAGES;
            }
        });
        TextView next = findViewById(R.id.welcomeNext);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                next.setText(position == PAGES - 1 ? R.string.welcome_start_fresh : R.string.welcome_next);
            }
        });
        next.setOnClickListener(v -> {
            if (pager.getCurrentItem() < PAGES - 1) {
                pager.setCurrentItem(pager.getCurrentItem() + 1, true);
            } else {
                finish();
            }
        });
        findViewById(R.id.welcomeSkip).setOnClickListener(v -> finish());
    }

    private void refreshPage() {
        if (pager != null && pager.getAdapter() != null) {
            pager.getAdapter().notifyItemChanged(pager.getCurrentItem());
        }
    }

    private void bindPage(View page, int position) {
        ((TextView) page.findViewById(R.id.welcomeStep)).setText(getString(R.string.welcome_step,
                position + 1, PAGES));
        TextView title = page.findViewById(R.id.welcomeTitle);
        TextView body = page.findViewById(R.id.welcomeBody);
        LinearLayout picture = page.findViewById(R.id.welcomePicture);
        LinearLayout actions = page.findViewById(R.id.welcomeActions);
        picture.removeAllViews();
        actions.removeAllViews();
        switch (position) {
            case 0:
                title.setText(R.string.welcome_worlds_title);
                body.setText(R.string.welcome_worlds_body);
                drawWorlds(picture);
                break;
            case 1:
                title.setText(R.string.welcome_tend_title);
                body.setText(R.string.welcome_tend_body);
                break;
            case 2:
                title.setText(R.string.welcome_tune_title);
                body.setText(R.string.welcome_tune_body);
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    action(actions, R.string.welcome_notifications, R.string.welcome_notifications_why, true, () ->
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS));
                }
                if (UpdateScheduler.isBatteryOptimised(this)) {
                    action(actions, R.string.welcome_battery, R.string.welcome_battery_why, true, () ->
                            UpdateScheduleFragment.fixBatteryOptimisation(this));
                }
                break;
            case 3:
            default:
                title.setText(R.string.welcome_ready_title);
                body.setText(R.string.welcome_ready_body);
                action(actions, R.string.welcome_moving, R.string.welcome_moving_why, true, () -> {
                    startActivity(new Intent(this, MainActivity.class).putExtra(EXTRA_OPEN_MOVING, true)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                });
                break;
        }
    }

    private void action(LinearLayout parent, int label, int why, boolean primary, Runnable onClick) {
        TextView button = new TextView(this);
        TextViewCompat.setTextAppearance(button, R.style.TextAppearance_WildInk_ItemTitle);
        button.setBackgroundResource(R.drawable.wildink_block);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        button.setPadding(padding, padding / 2, padding, padding / 2);
        button.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
        button.setGravity(Gravity.CENTER_VERTICAL);
        if (primary) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    ThemeUtils.getColorFromAttr(this, R.attr.colorPrimary)));
            button.setTextColor(ThemeUtils.getColorFromAttr(this, R.attr.colorOnPrimary));
            button.setGravity(Gravity.CENTER);
            button.setElevation(4 * getResources().getDisplayMetrics().density);
            TypedValue ripple = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            button.setForeground(ContextCompat.getDrawable(this, ripple.resourceId));
        }
        button.setText(label);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = padding;
        parent.addView(button, params);
        TextView note = new TextView(this);
        TextViewCompat.setTextAppearance(note, R.style.TextAppearance_WildInk_Detail);
        note.setText(why);
        note.setPadding(0, padding / 3, 0, 0);
        parent.addView(note);
    }

    private void drawWorlds(LinearLayout picture) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] names = {R.string.world_short_books, R.string.world_short_podcasts, R.string.world_short_radio};
        for (int i = 0; i < names.length; i++) {
            TextView world = new TextView(this);
            TextViewCompat.setTextAppearance(world, i == 1 ? R.style.TextAppearance_WildInk_World
                    : R.style.TextAppearance_WildInk_WorldOther);
            world.setText(names[i]);
            world.setGravity(Gravity.CENTER);
            world.setMaxLines(1);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(world, 10, i == 1 ? 34 : 16, 1,
                    TypedValue.COMPLEX_UNIT_SP);
            row.addView(world, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMarginEnd((int) (24 * density));
        picture.addView(row, rowParams);
        FrameLayout play = new FrameLayout(this);
        play.setBackgroundResource(R.drawable.wildink_play_circle);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.wildink_play);
        icon.setColorFilter(ThemeUtils.getColorFromAttr(this, R.attr.colorOnPrimary));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = (int) (64 * density);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize,
                Gravity.CENTER_VERTICAL | Gravity.START);
        iconParams.setMarginStart((int) (18 * density));
        play.addView(icon, iconParams);
        int size = (int) (150 * density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.topMargin = (int) (24 * density);
        params.gravity = Gravity.END;
        params.setMarginEnd(-size / 3);
        picture.addView(play, params);
    }
}
