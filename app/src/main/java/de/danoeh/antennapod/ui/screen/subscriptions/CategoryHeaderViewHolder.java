package de.danoeh.antennapod.ui.screen.subscriptions;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import de.danoeh.antennapod.R;

public class CategoryHeaderViewHolder extends SubscriptionViewHolder {
    public final TextView headerTitle;

    public CategoryHeaderViewHolder(@NonNull View itemView, Activity mainActivity) {
        super(itemView, mainActivity);
        headerTitle = itemView.findViewById(R.id.categoryHeaderTitle);
    }
}
