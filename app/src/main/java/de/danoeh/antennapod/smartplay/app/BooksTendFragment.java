package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BooksTendFragment extends Fragment {
    private static final String TAG = "BooksTend";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private View root;

    private final ActivityResultLauncher<Uri> addFolder =
            registerForActivityResult(new PickFolder(), this::addFolderResult);

    private static final class BookRow {
        Feed feed;
        String progress;
    }

    private static final class State {
        final List<BookRow> books = new ArrayList<>();
        final List<Feed> folders = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.tend_fragment, container, false);
        TendRows.setUp(this, root, R.string.world_books, R.string.tend_find_book,
                () -> TendRows.open(this, LibriVoxSearchFragment.newInstance(null)));
        ((TextView) root.findViewById(R.id.tendListLabel)).setText(R.string.tend_books);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            State state = new State();
            Set<Long> books = Books.bookIds(app);
            Map<Long, Long> lastPlayed = Books.lastPlayedPerBook(app, books);
            List<Feed> bookFeeds = new ArrayList<>();
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                    continue;
                }
                if (books.contains(feed.getId())) {
                    bookFeeds.add(feed);
                } else if (feed.isLocalFeed() && !Recordings.isRecordingsFeed(feed)) {
                    state.folders.add(feed);
                }
            }
            Collections.sort(bookFeeds, (a, b) -> Long.compare(value(lastPlayed, b), value(lastPlayed, a)));
            for (Feed feed : bookFeeds) {
                BookRow row = new BookRow();
                row.feed = feed;
                Books.Progress progress = Books.progress(app, feed);
                row.progress = progress.isFinished() || Books.isFinished(app, feed.getId())
                        ? app.getString(R.string.tend_book_finished)
                        : app.getString(R.string.tend_book_progress, Math.max(1, progress.chapter), progress.chapters);
                state.books.add(row);
            }
            return state;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private static long value(Map<Long, Long> map, Feed feed) {
        Long value = map.get(feed.getId());
        return value != null ? value : 0;
    }

    private void show(State state) {
        if (root == null) {
            return;
        }
        LinearLayout list = root.findViewById(R.id.tendList);
        list.removeAllViews();
        for (BookRow book : state.books) {
            list.addView(TendRows.row(this, list, book.feed.getTitle(), book.progress, book.feed.getImageUrl(),
                    "", "", v -> TendRows.open(this, FeedItemlistFragment.newInstance(book.feed.getId()))));
        }
        if (state.books.isEmpty()) {
            TextView empty = new TextView(requireContext());
            TextViewCompat.setTextAppearance(empty, R.style.TextAppearance_WildInk_Detail);
            empty.setText(R.string.tend_no_books);
            empty.setPadding(0, dp(12), 0, 0);
            list.addView(empty);
        }
        LinearLayout extra = root.findViewById(R.id.tendExtra);
        extra.removeAllViews();
        TextView label = new TextView(requireContext());
        TextViewCompat.setTextAppearance(label, R.style.TextAppearance_WildInk_Label);
        label.setText(R.string.tend_folders);
        label.setPadding(0, dp(28), 0, dp(4));
        extra.addView(label);
        for (Feed folder : state.folders) {
            extra.addView(TendRows.row(this, extra, folder.getTitle(), null, folder.getImageUrl(), "", "",
                    v -> TendRows.open(this, FeedItemlistFragment.newInstance(folder.getId()))));
        }
        TextView add = new TextView(requireContext());
        TextViewCompat.setTextAppearance(add, R.style.Widget_WildInk_Link);
        add.setBackgroundResource(R.drawable.wildink_block);
        add.setMinHeight(dp(48));
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setPadding(dp(18), 0, dp(18), 0);
        add.setText(R.string.tend_add_folder);
        add.setOnClickListener(v -> addFolder.launch(null));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        extra.addView(add, params);
    }

    private void addFolderResult(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            app.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            DocumentFile documentFile = DocumentFile.fromTreeUri(app, uri);
            String title = documentFile != null ? documentFile.getName() : null;
            if (title == null) {
                title = app.getString(R.string.local_folder);
            }
            Feed folder = new Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title);
            folder.setItems(Collections.emptyList());
            folder.setSortOrder(SortOrder.EPISODE_FILENAME_A_Z);
            Feed saved = FeedDatabaseWriter.updateFeed(app, folder, false);
            FeedUpdateManager.getInstance().runOnce(app, saved);
            return saved;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(saved -> load(), error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private static final class PickFolder extends ActivityResultContracts.OpenDocumentTree {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Uri input) {
            return super.createIntent(context, input)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
