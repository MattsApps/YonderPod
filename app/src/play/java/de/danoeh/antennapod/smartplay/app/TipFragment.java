package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.billingclient.api.ProductDetails;
import com.google.android.material.color.MaterialColors;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.R;

/** The tip sheet (Play build): a line from Matt and four amounts, priced locally by Play. */
public class TipFragment extends Fragment {
    private static final int[] CHOICES = {R.id.tipChoice0, R.id.tipChoice1, R.id.tipChoice2, R.id.tipChoice3};
    private static final long CLOSE_AFTER_THANKS = 1500;

    private View root;
    @Nullable
    private ProductDetails details;
    private boolean loaded;
    private boolean closing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        TipShop.problem = null;
        root = inflater.inflate(R.layout.backup_fragment, container, false);
        ((TextView) root.findViewById(R.id.pageTitle)).setText(R.string.tip_title);
        ((TextView) root.findViewById(R.id.pageIntro)).setText(R.string.tip_line);
        root.findViewById(R.id.pageAction).setVisibility(View.GONE);
        LinearLayout sections = root.findViewById(R.id.pageSections);
        inflater.inflate(R.layout.tip_choices, sections, true);
        root.findViewById(R.id.tipNotToday).setOnClickListener(v -> {
            Tip.dismissForToday(requireContext());
            getParentFragmentManager().popBackStack();
        });
        show();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        TipShop.refresh(requireContext());
        TipShop.details(requireContext(), found -> {
            details = found;
            loaded = true;
            show();
        });
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

    private void show() {
        if (root == null || getContext() == null) {
            return;
        }
        if (Tip.thanksLeft(requireContext()) > 0 && !closing) {
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

        String[] fallback = getResources().getStringArray(R.array.tip_amounts);
        for (int i = 0; i < CHOICES.length; i++) {
            TextView choice = root.findViewById(CHOICES[i]);
            ProductDetails.SubscriptionOfferDetails offer = TipShop.offer(details, TipShop.PLANS[i]);
            String price = offer != null ? TipShop.price(offer) : null;
            choice.setText(price != null ? price : fallback[i]);
            choice.setEnabled(offer != null);
            choice.setAlpha(offer != null ? 1f : 0.4f);
            choice.setOnClickListener(offer == null ? null
                    : v -> TipShop.buy(requireActivity(), details, offer));
        }

        boolean supporter = Tip.isSupporter(requireContext());
        TextView note = root.findViewById(R.id.pageActionNote);
        String until = Tip.supporterUntilText(requireContext());
        if (TipShop.problem != null) {
            note.setText(getString(R.string.tip_problem, TipShop.problem));
        } else if (TipShop.pending) {
            note.setText(R.string.tip_pending);
        } else if (supporter) {
            note.setText(until != null ? getString(R.string.tip_supporter_until, until)
                    : getString(R.string.tip_supporter));
        } else if (!loaded) {
            note.setText(R.string.tip_loading);
        } else if (details == null) {
            note.setText(R.string.tip_unavailable);
        } else {
            note.setText("");
        }
        note.setVisibility(note.getText().length() == 0 ? View.GONE : View.VISIBLE);

        String explain = getString(R.string.tip_explain);
        if (supporter) {
            explain += " " + getString(R.string.tip_explain_again);
        }
        ((TextView) root.findViewById(R.id.tipExplain)).setText(explain);
        root.findViewById(R.id.tipNotToday).setVisibility(Tip.showTipMe(requireContext()) ? View.VISIBLE : View.GONE);
    }
}
