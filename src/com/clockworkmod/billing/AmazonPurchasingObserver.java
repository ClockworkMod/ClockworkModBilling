package com.clockworkmod.billing;

import android.content.Context;

import com.amazon.inapp.purchasing.BasePurchasingObserver;
import com.amazon.inapp.purchasing.GetUserIdResponse;
import com.amazon.inapp.purchasing.ItemDataResponse;
import com.amazon.inapp.purchasing.Offset;
import com.amazon.inapp.purchasing.PurchaseResponse;
import com.amazon.inapp.purchasing.PurchaseUpdatesResponse;
import com.amazon.inapp.purchasing.PurchasingManager;

public class AmazonPurchasingObserver extends BasePurchasingObserver {
    private static final String TAG = "AmazonPurchasingObserver";
    private boolean rvsProductionMode = false;
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

    @Override
    public void onSdkAvailable(boolean isSandboxMode) {
        super.onSdkAvailable(isSandboxMode);
        rvsProductionMode = !isSandboxMode;
    }

    private String currentUserID = null;

    @Override
    public void onGetUserIdResponse(final GetUserIdResponse response) {
        if (response.getUserIdRequestStatus() ==
        GetUserIdResponse.GetUserIdRequestStatus.SUCCESSFUL) {
            currentUserID = response.getUserId();
            PurchasingManager.initiatePurchaseUpdatesRequest(getPersistedOffset());
        } else {
            // Fail gracefully.
        }
    }

    Offset getPersistedOffset() {
        return persistedOffset;
    }

    @Override
    public void onItemDataResponse(final ItemDataResponse response) {
    }

    @Override
    public void onPurchaseResponse(final PurchaseResponse response) {
    }

    @Override
    public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
    }
}