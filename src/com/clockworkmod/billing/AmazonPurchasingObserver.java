package com.clockworkmod.billing;

import android.content.Context;

import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.UserDataResponse;

public class AmazonPurchasingObserver implements PurchasingListener {
    private static final String TAG = "AmazonPurchasingObserver";
    public AmazonPurchasingObserver(Context context) {
    }

    @Override
    public void onUserDataResponse(UserDataResponse userDataResponse) {

    }

    @Override
    public void onProductDataResponse(ProductDataResponse productDataResponse) {

    }

    @Override
    public void onPurchaseResponse(PurchaseResponse purchaseResponse) {

    }

    @Override
    public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse purchaseUpdatesResponse) {

    }
}