package de.danoeh.antennapod.smartplay.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.common.ThemeSwitcher;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class OpenAudioActivity extends AppCompatActivity {
    private static final String TAG = "OpenAudioActivity";
    private static final int PLAY_NOW = 0;
    private static final int PLAY_NEXT = 1;
    private static final int ADD = 2;

    private Disposable disposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeSwitcher.getTranslucentTheme(this));
        super.onCreate(savedInstanceState);
        Uri uri = audioUri(getIntent());
        if (uri == null) {
            finish();
            return;
        }
        String[] choices = {
            getString(R.string.open_file_play_now),
            getString(R.string.open_file_play_next),
            getString(R.string.open_file_add)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(uri.getLastPathSegment())
                .setItems(choices, (dialog, which) -> addAndAct(uri, which))
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void addAndAct(Uri uri, int action) {
        disposable = Single.fromCallable(() -> AddedFiles.add(getApplicationContext(), uri))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (action == PLAY_NOW && result.item.getMedia() != null) {
                        new PlaybackServiceStarter(this, result.item.getMedia())
                                .callEvenIfRunning(true)
                                .start();
                    } else if (action == PLAY_NEXT) {
                        SmartPlayQueueManager.getInstance().playNext(result.item);
                    } else if (action == ADD) {
                        DBWriter.addQueueItem(getApplicationContext(), result.item);
                    }
                    String message = result.copied
                            ? getString(R.string.open_file_copied, Formatter.formatShortFileSize(this, result.size))
                            : getString(R.string.open_file_linked);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    finish();
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(getApplicationContext(), R.string.open_file_failed, Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private static Uri audioUri(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        return intent.getData();
    }
}
