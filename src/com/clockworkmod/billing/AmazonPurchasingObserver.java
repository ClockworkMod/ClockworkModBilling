package com.clockworkmod.billing;

import android.content.Context;

import com.amazon.inapp.purchasing.BasePurchasingObserver;
import com.amazon.inapp.purchasing.Offset;

public class AmazonPurchasingObserver extends BasePurchasingObserver {
    private static final String TAG = "AmazonPurchasingObserver";
    Offset persistedOffset;

    public AmazonPurchasingObserver(Context context) {
        super(context);

        String offset = context.getSharedPreferences("amazon", Context.MODE_PRIVATE)
        .getString("persistedOffset", null);

        if (offset != null)
            persistedOffset = Offset.fromString(offset);
        else
            persistedOffset = Offset.BEGINNING;
    }

    Offset getPersistedOffset() {
        return persistedOffset;
    }
}