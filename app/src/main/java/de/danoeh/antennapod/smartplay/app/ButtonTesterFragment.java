package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.playback.ButtonLogEvent;
import de.danoeh.antennapod.storage.preferences.ControlsPreferences;

public class ButtonTesterFragment extends Fragment {
    private TextView log;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.button_tester_fragment, container, false);
        log = root.findViewById(R.id.button_tester_log);
        log.setMovementMethod(new ScrollingMovementMethod());
        root.findViewById(R.id.button_tester_clear).setOnClickListener(v -> {
            ControlsPreferences.clearLog();
            refresh();
        });
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof AppCompatActivity
                && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.button_tester_title);
        }
        ControlsPreferences.setTesting(true);
        EventBus.getDefault().register(this);
        refresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        ControlsPreferences.setTesting(false);
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onButtonLog(ButtonLogEvent event) {
        refresh();
    }

    private void refresh() {
        List<String> lines = ControlsPreferences.getLog();
        log.setText(lines.isEmpty() ? getString(R.string.button_tester_empty) : TextUtils.join("\n", lines));
    }
}
