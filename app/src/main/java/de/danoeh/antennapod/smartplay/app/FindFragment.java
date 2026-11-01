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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.Keyboard;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class FindFragment extends Fragment {
    private static final String TAG = "FindFragment";
    protected static final String ARG_QUERY = "query";

    protected static final class Result {
        public String title = "";
        public String meta = "";
        public String artUrl;
        public boolean done;
        public boolean choice;
        public String choiceQuery;
        public Object payload;
    }

    protected final CompositeDisposable disposables = new CompositeDisposable();
    private final List<Result> results = new ArrayList<>();
    private Disposable searching;
    private ResultAdapter adapter;
    private EditText query;
    private TextView status;
    private LinearLayout tabs;
    private int tab;

    @StringRes
    protected abstract int world();

    @StringRes
    protected abstract int hint();

    protected abstract int[] tabLabels();

    @StringRes
    protected abstract int actionLabel();

    @StringRes
    protected abstract int doneLabel();

    @StringRes
    protected abstract int footer();

    protected abstract List<Result> fetch(int tab, String query) throws Exception;

    protected abstract void act(Result result, Runnable onDone);

    protected abstract void open(Result result);

    protected void onFooter() {
    }

    protected boolean footerIsLink() {
        return false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.find_fragment, container, false);
        ((TextView) root.findViewById(R.id.findWorld)).setText(world());
        query = root.findViewById(R.id.findQuery);
        query.setHint(hint());
        status = root.findViewById(R.id.findStatus);
        tabs = root.findViewById(R.id.findTabs);
        ListView list = root.findViewById(R.id.findList);
        adapter = new ResultAdapter();
        list.setAdapter(adapter);
        TextView footerView = root.findViewById(R.id.findFooter);
        footerView.setText(footer());
        if (footerIsLink()) {
            footerView.setBackgroundResource(R.drawable.wildink_block);
            footerView.setPadding(dp(16), 0, dp(16), 0);
            footerView.setTextColor(footerView.getCurrentTextColor());
            footerView.setOnClickListener(v -> onFooter());
        }
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null) {
                Keyboard.hide(getActivity());
                select(0, query.getText().toString());
                return true;
            }
            return false;
        });
        int[] labels = tabLabels();
        for (int i = 0; i < labels.length; i++) {
            View tabView = inflater.inflate(R.layout.find_tab, tabs, false);
            ((TextView) tabView.findViewById(R.id.tabLabel)).setText(labels[i]);
            int index = i;
            tabView.setOnClickListener(v -> select(index, query.getText().toString()));
            tabs.addView(tabView);
        }
        String initial = getArguments() != null ? getArguments().getString(ARG_QUERY) : null;
        if (!TextUtils.isEmpty(initial)) {
            query.setText(initial);
            select(0, initial);
        } else {
            select(labels.length > 1 ? 1 : 0, "");
        }
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        if (searching != null) {
            searching.dispose();
        }
    }

    private void showTabs() {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View tabView = tabs.getChildAt(i);
            TextView label = tabView.findViewById(R.id.tabLabel);
            boolean selected = i == tab;
            label.setTypeface(ResourcesCompat.getFont(requireContext(),
                    selected ? R.font.bricolage_extrabold : R.font.bricolage_regular));
            label.setTextColor(selected ? query.getCurrentTextColor() : query.getCurrentHintTextColor());
            tabView.findViewById(R.id.tabLine).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    protected void select(int newTab, String text) {
        tab = newTab;
        showTabs();
        if (searching != null) {
            searching.dispose();
        }
        results.clear();
        adapter.notifyDataSetChanged();
        String trimmed = text.trim();
        if (newTab == 0 && trimmed.isEmpty()) {
            status.setText(R.string.find_type_above);
            status.setVisibility(View.VISIBLE);
            return;
        }
        status.setText(R.string.find_searching);
        status.setVisibility(View.VISIBLE);
        searching = Single.fromCallable(() -> fetch(newTab, trimmed))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(found -> {
                    results.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        status.setText(getString(R.string.find_nothing, trimmed));
                    } else {
                        status.setVisibility(View.GONE);
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    status.setText(R.string.find_error);
                    status.setVisibility(View.VISIBLE);
                });
    }

    private void onChoice(Result result) {
        query.setText(result.title);
        tab = 0;
        showTabs();
        select(0, result.choiceQuery != null ? result.choiceQuery : result.title);
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private final class ResultAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public Result getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.find_result_row, parent, false);
            Result result = getItem(position);
            ((TextView) view.findViewById(R.id.resultTitle)).setText(result.title);
            TextView meta = view.findViewById(R.id.resultMeta);
            meta.setText(result.meta);
            meta.setVisibility(result.meta == null || result.meta.isEmpty() ? View.GONE : View.VISIBLE);
            view.findViewById(R.id.resultArtBox).setVisibility(result.choice ? View.GONE : View.VISIBLE);
            ((TextView) view.findViewById(R.id.resultInitials)).setText(TendRows.initials(result.title));
            ImageView art = view.findViewById(R.id.resultArt);
            Glide.with(FindFragment.this).clear(art);
            art.setImageDrawable(null);
            if (result.artUrl != null && !result.artUrl.isEmpty()) {
                Glide.with(FindFragment.this).load(result.artUrl).into(art);
            }
            TextView action = view.findViewById(R.id.resultAction);
            TextView done = view.findViewById(R.id.resultDone);
            action.setText(actionLabel());
            done.setText(doneLabel());
            action.setVisibility(!result.choice && !result.done ? View.VISIBLE : View.GONE);
            done.setVisibility(!result.choice && result.done ? View.VISIBLE : View.GONE);
            action.setOnClickListener(v -> act(result, () -> {
                result.done = true;
                notifyDataSetChanged();
            }));
            view.setOnClickListener(v -> {
                if (result.choice) {
                    onChoice(result);
                } else {
                    open(result);
                }
            });
            return view;
        }
    }
}
