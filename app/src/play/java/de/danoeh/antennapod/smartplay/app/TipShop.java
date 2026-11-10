package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import de.danoeh.antennapod.R;

/**
 * Google Play build: tips are one-year prepaid plans (no auto-renew) of one subscription, so a tip follows
 * the listener across devices and reinstalls and ends by itself. Buying again before the year is up is a
 * Play "top-up": charged straight away, and the end date moves on by a year.
 *
 * <p>Play Console must have subscription {@link #PRODUCT} with the prepaid base plans in {@link #PLANS}.
 */
final class TipShop {
    private static final String TAG = "TipShop";
    static final String PRODUCT = "supporter";
    static final String[] PLANS = {"year-3", "year-6", "year-12", "year-24"};

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Runnable> WHEN_READY = new ArrayList<>();
    private static final List<Runnable> WHEN_FAILED = new ArrayList<>();

    @Nullable
    private static BillingClient client;
    private static Context app;
    private static boolean connecting;

    /** Set while Play says a tip is waiting for payment (for example a cash payment). */
    static boolean pending;
    /** The last thing that went wrong in the purchase flow, shown on the tip sheet; null if nothing. */
    @Nullable
    static String problem;

    private TipShop() {
    }

    static boolean canTip(Context context) {
        return true;
    }

    static void open(Fragment fragment) {
        TendRows.open(fragment, new TipFragment());
    }

    /** Asks Play for this listener's active tip. Errors or no network leave the last answer in place. */
    static void refresh(Context context) {
        connect(context, () -> client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build(),
                (result, purchases) -> MAIN.post(() -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase check failed: " + result.getResponseCode() + " "
                                + result.getDebugMessage());
                        return;
                    }
                    handle(purchases, false);
                })), () -> Log.d(TAG, "Play not reachable for the purchase check"));
    }

    /** Loads the subscription with its local prices; null if Play can't be reached or it isn't set up. */
    static void details(Context context, Consumer<ProductDetails> done) {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()))
                .build();
        connect(context, () -> client.queryProductDetailsAsync(params, (result, found) -> MAIN.post(() -> {
            List<ProductDetails> list = found != null ? found.getProductDetailsList() : null;
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null || list.isEmpty()) {
                Log.d(TAG, "No product details: " + result.getResponseCode() + " " + result.getDebugMessage());
                done.accept(null);
                return;
            }
            done.accept(list.get(0));
        })), () -> done.accept(null));
    }

    /** The plan's offer, or null if that base plan isn't active in Play Console. */
    @Nullable
    static ProductDetails.SubscriptionOfferDetails offer(@Nullable ProductDetails details, String plan) {
        if (details == null || details.getSubscriptionOfferDetails() == null) {
            return null;
        }
        for (ProductDetails.SubscriptionOfferDetails offer : details.getSubscriptionOfferDetails()) {
            if (plan.equals(offer.getBasePlanId()) && offer.getOfferId() == null) {
                return offer;
            }
        }
        return null;
    }

    @Nullable
    static String price(ProductDetails.SubscriptionOfferDetails offer) {
        List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
        return phases.isEmpty() ? null : phases.get(phases.size() - 1).getFormattedPrice();
    }

    /** Opens Play's purchase sheet. A listener who has already tipped tops up: Play adds a year. */
    static void buy(Activity activity, ProductDetails details, ProductDetails.SubscriptionOfferDetails offer) {
        problem = null;
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .setOfferToken(offer.getOfferToken())
                                .build()))
                .build();
        connect(activity, () -> {
            BillingResult result = client.launchBillingFlow(activity, params);
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                failed(result);
            }
        }, () -> {
            problem = activity.getString(R.string.tip_unavailable);
            Tip.changed();
        });
    }

    private static void onFlowResult(BillingResult result, @Nullable List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            handle(purchases, true);
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refresh(app);
        } else if (code != BillingClient.BillingResponseCode.USER_CANCELED) {
            failed(result);
        }
    }

    private static void failed(BillingResult result) {
        Log.d(TAG, "Purchase flow: " + result.getResponseCode() + " " + result.getDebugMessage());
        String message = result.getDebugMessage();
        problem = message == null || message.isEmpty() ? String.valueOf(result.getResponseCode()) : message;
        Tip.changed();
    }

    private static void handle(List<Purchase> purchases, boolean fromFlow) {
        Purchase found = null;
        boolean waiting = false;
        for (Purchase purchase : purchases) {
            if (!purchase.getProducts().contains(PRODUCT)) {
                continue;
            }
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                found = purchase;
            } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                waiting = true;
            }
        }
        pending = waiting && found == null;
        if (found != null) {
            Tip.playSays(app, found.getPurchaseToken(), found.getPurchaseTime());
            if (!found.isAcknowledged()) {
                acknowledge(found.getPurchaseToken());
            }
            if (fromFlow) {
                Tip.thank(app);
            }
        } else if (!fromFlow) {
            Tip.playSays(app, null, 0);
        } else {
            Tip.changed();
        }
    }

    /** Play refunds a purchase that isn't acknowledged within 3 days. Retried at every app start. */
    private static void acknowledge(String token) {
        connect(app, () -> client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token).build(), result -> Log.d(TAG, "Acknowledged: " + result.getResponseCode())),
                () -> Log.d(TAG, "Couldn't acknowledge yet; will try at the next start"));
    }

    private static void connect(Context context, Runnable ready, Runnable failed) {
        app = context.getApplicationContext();
        if (client == null) {
            client = BillingClient.newBuilder(app)
                    .setListener((result, purchases) -> MAIN.post(() -> onFlowResult(result, purchases)))
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                            .enableOneTimeProducts()
                            .enablePrepaidPlans()
                            .build())
                    .enableAutoServiceReconnection()
                    .build();
        }
        if (client.isReady()) {
            ready.run();
            return;
        }
        WHEN_READY.add(ready);
        WHEN_FAILED.add(failed);
        if (connecting) {
            return;
        }
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                MAIN.post(() -> finish(result.getResponseCode() == BillingClient.BillingResponseCode.OK));
            }

            @Override
            public void onBillingServiceDisconnected() {
                MAIN.post(() -> finish(false));
            }
        });
    }

    private static void finish(boolean ok) {
        connecting = false;
        List<Runnable> run = new ArrayList<>(ok ? WHEN_READY : WHEN_FAILED);
        WHEN_READY.clear();
        WHEN_FAILED.clear();
        for (Runnable runnable : run) {
            runnable.run();
        }
    }
}
