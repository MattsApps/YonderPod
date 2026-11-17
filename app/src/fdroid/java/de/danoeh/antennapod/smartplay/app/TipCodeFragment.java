package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.IntentUtils;

/** The tip screen (F-Droid build): Matt's line, four Buy Me a Coffee shop items, and a field for the code. */
public class TipCodeFragment extends Fragment {
    private static final int[] CHOICES = {R.id.tipChoice0, R.id.tipChoice1, R.id.tipChoice2, R.id.tipChoice3};
    private static final long CLOSE_AFTER_THANKS = 1500;
    private static final String KEY_OPENED_AT = "openedAt";

    private View root;
    private boolean wrong;
    private boolean closing;
    private long openedAt;
    private int softInputBefore = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openedAt = savedInstanceState != null ? savedInstanceState.getLong(KEY_OPENED_AT)
                : System.currentTimeMillis();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_OPENED_AT, openedAt);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.backup_fragment, container, false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean keyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
            int bottom = bars.bottom;
            if (keyboard) {
                int[] at = new int[2];
                v.getLocationOnScreen(at);
                int below = Math.max(0, v.getRootView().getHeight() - (at[1] + v.getHeight()));
                bottom = Math.max(bars.bottom, ime.bottom - below);
            }
            v.setPadding(bars.left, bars.top, bars.right, bottom);
            if (keyboard) {
                v.post(this::showField);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ((TextView) root.findViewById(R.id.pageTitle)).setText(R.string.tip_title);
        ((TextView) root.findViewById(R.id.pageIntro)).setText(R.string.tip_line);
        root.findViewById(R.id.pageAction).setVisibility(View.GONE);
        LinearLayout sections = root.findViewById(R.id.pageSections);
        inflater.inflate(R.layout.tip_code_choices, sections, true);

        String[] amounts = getResources().getStringArray(R.array.tip_amounts);
        for (int i = 0; i < CHOICES.length; i++) {
            String link = TipCodes.SHOP_ITEMS[i];
            TextView choice = root.findViewById(CHOICES[i]);
            choice.setText(amounts[i]);
            choice.setOnClickListener(v -> IntentUtils.openInBrowser(requireContext(), link));
        }

        EditText field = root.findViewById(R.id.tipCodeField);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                enter();
                return true;
            }
            return false;
        });
        root.findViewById(R.id.tipCodeEnter).setOnClickListener(v -> enter());

        root.findViewById(R.id.tipNotToday).setOnClickListener(v -> {
            Tip.dismissForToday(requireContext());
            getParentFragmentManager().popBackStack();
        });

        TextView test = root.findViewById(R.id.tipCodeTest);
        test.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        test.setOnClickListener(v -> {
            Tip.testCodeYearUp(requireContext());
            getParentFragmentManager().popBackStack();
        });
        show();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // The keyboard makes room inside this screen, rather than sliding the whole screen up.
        WindowManager.LayoutParams attributes = requireActivity().getWindow().getAttributes();
        softInputBefore = attributes.softInputMode;
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | (softInputBefore & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (softInputBefore != -1) {
            requireActivity().getWindow().setSoftInputMode(softInputBefore);
            softInputBefore = -1;
        }
    }

    /** With the keyboard up, scrolls just enough to show the code field and its enter button above it. */
    private void showField() {
        if (!(root instanceof NestedScrollView) || getContext() == null) {
            return;
        }
        View field = root.findViewById(R.id.tipCodeField);
        if (!field.hasFocus()) {
            return;
        }
        Rect rect = new Rect();
        field.getDrawingRect(rect);
        ((ViewGroup) root).offsetDescendantRectToMyCoords(field, rect);
        int gap = Math.round(16 * getResources().getDisplayMetrics().density);
        int target = rect.bottom + gap - (root.getHeight() - root.getPaddingBottom());
        if (target > root.getScrollY()) {
            ((NestedScrollView) root).smoothScrollTo(0, target);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTipChanged(Tip.ChangedEvent event) {
        show();
    }

    private void enter() {
        EditText field = root.findViewById(R.id.tipCodeField);
        if (TipCodes.matches(field.getText().toString())) {
            wrong = false;
            field.setText("");
            InputMethodManager keyboard = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
            }
            Tip.codeEntered(requireContext());
            Tip.thank(requireContext());
        } else {
            wrong = true;
            show();
        }
    }

    private void show() {
        if (root == null || getContext() == null) {
            return;
        }
        if (Tip.thanksLeft(requireContext()) > 0 && Tip.thanksAt(requireContext()) >= openedAt && !closing) {
            closing = true;
            TextView title = root.findViewById(R.id.pageTitle);
            title.setText(R.string.tip_thanks_title);
            title.setTextColor(MaterialColors.getColor(title, R.attr.colorPrimary));
            root.postDelayed(() -> {
                if (isAdded() && !isStateSaved()) {
                    getParentFragmentManager().popBackStack();
                }
            }, CLOSE_AFTER_THANKS);
        }

        boolean supporter = Tip.isSupporter(requireContext());
        TextView note = root.findViewById(R.id.tipCodeNote);
        String until = Tip.supporterUntilText(requireContext());
        if (wrong) {
            note.setText(R.string.tip_code_wrong);
        } else if (supporter) {
            note.setText(until != null ? getString(R.string.tip_supporter_until, until)
                    : getString(R.string.tip_supporter));
        } else {
            note.setText("");
        }
        note.setVisibility(note.getText().length() == 0 ? View.GONE : View.VISIBLE);

        String explain = getString(R.string.tip_explain);
        if (supporter) {
            explain += " " + getString(R.string.tip_code_again);
        }
        ((TextView) root.findViewById(R.id.tipExplain)).setText(explain);
        root.findViewById(R.id.tipNotToday).setVisibility(Tip.showTipMe(requireContext()) ? View.VISIBLE : View.GONE);
    }
}
