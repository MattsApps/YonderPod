package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.Keyboard;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class LibriVoxSearchFragment extends Fragment {
    private static final String TAG = "LibriVoxSearch";
    private static final String ARG_QUERY = "query";
    private static final int READERS_SHOWN = 2;

    private final List<LibriVox.Book> results = new ArrayList<>();
    private ResultAdapter adapter;
    private ProgressBar progress;
    private TextView message;
    private Disposable disposable;

    public static LibriVoxSearchFragment newInstance(@Nullable String query) {
        LibriVoxSearchFragment fragment = new LibriVoxSearchFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.librivox_search_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, true);
        progress = root.findViewById(R.id.librivox_progress);
        message = root.findViewById(R.id.librivox_message);
        ListView list = root.findViewById(R.id.librivox_results);
        adapter = new ResultAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> startActivity(
                new OnlineFeedviewActivityStarter(getContext(), results.get(position).rssUrl).getIntent()));

        EditText query = root.findViewById(R.id.librivox_query);
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null) {
                search(query.getText().toString());
                Keyboard.hide(getActivity());
                return true;
            }
            return false;
        });
        String initial = getArguments() != null ? getArguments().getString(ARG_QUERY) : null;
        if (!TextUtils.isEmpty(initial)) {
            query.setText(initial);
            search(initial);
        } else {
            query.requestFocus();
            Keyboard.show(getContext(), query);
        }
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void search(String query) {
        if (TextUtils.isEmpty(query.trim())) {
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        results.clear();
        adapter.notifyDataSetChanged();
        message.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        disposable = Single.fromCallable(() -> LibriVox.search(query))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(books -> {
                    progress.setVisibility(View.GONE);
                    results.addAll(books);
                    adapter.notifyDataSetChanged();
                    if (books.isEmpty()) {
                        message.setText(getString(R.string.librivox_no_results, query.trim()));
                        message.setVisibility(View.VISIBLE);
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    progress.setVisibility(View.GONE);
                    message.setText(R.string.librivox_error);
                    message.setVisibility(View.VISIBLE);
                });
    }

    private String readers(LibriVox.Book book) {
        if (book.readers.isEmpty()) {
            return "";
        }
        if (book.readers.size() <= READERS_SHOWN) {
            return getString(R.string.librivox_read_by, TextUtils.join(", ", book.readers));
        }
        return getString(R.string.librivox_read_by_many,
                TextUtils.join(", ", book.readers.subList(0, READERS_SHOWN)));
    }

    private final class ResultAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public LibriVox.Book getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.librivox_result_item, parent, false);
            LibriVox.Book book = getItem(position);
            ((TextView) view.findViewById(R.id.librivox_book_title)).setText(book.title);
            TextView author = view.findViewById(R.id.librivox_book_author);
            author.setText(book.authors);
            author.setVisibility(book.authors.isEmpty() ? View.GONE : View.VISIBLE);
            List<String> details = new ArrayList<>();
            String readBy = readers(book);
            if (!readBy.isEmpty()) {
                details.add(readBy);
            }
            if (book.seconds > 0) {
                details.add(Converter.getDurationStringLocalized(getResources(), book.seconds * 1000, false));
            }
            if (!book.language.isEmpty() && !"English".equals(book.language)) {
                details.add(book.language);
            }
            ((TextView) view.findViewById(R.id.librivox_book_details)).setText(TextUtils.join(" · ", details));
            ImageView cover = view.findViewById(R.id.librivox_cover);
            Glide.with(LibriVoxSearchFragment.this).load(book.coverUrl).placeholder(R.color.light_gray)
                    .error(R.color.light_gray).into(cover);
            return view;
        }
    }
}
