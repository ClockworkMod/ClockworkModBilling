package com.clockworkmod.billing;

import android.content.Context;

import com.amazon.inapp.purchasing.BasePurchasingObserver;
import com.amazon.inapp.purchasing.GetUserIdResponse;
import com.amazon.inapp.purchasing.ItemDataResponse;
import com.amazon.inapp.purchasing.Offset;
import com.amazon.inapp.purchasing.PurchaseResponse;
import com.amazon.inapp.purchasing.PurchaseUpdatesResponse;
import com.amazon.inapp.purchasing.PurchasingManager;

public class AmazonHelper extends BasePurchasingObserver {
    private AmazonHelper(Context context) {
        super(context);
    }
    
    private static <T> void invokeCallback(String callbackToken, T response) {
        Callback callback = mCallbacks.get(callbackToken);
        if (callback == null)
            return;
        callback.onFinished(response);
    }
    
    private static Boolean mIsSandbox;
    public static Boolean getIsSandbox() {
        return mIsSandbox;
    }
    
    
    static String mUserId;
    public static void initialize(Context context) {
        PurchasingManager.registerObserver(new AmazonHelper(context) {
            @Override
            public void onSdkAvailable(boolean sandbox) {
                super.onSdkAvailable(sandbox);
                mIsSandbox = sandbox;
            }
            
            @Override
            public void onPurchaseResponse(PurchaseResponse r) {
                super.onPurchaseResponse(r);
                invokeCallback(r.getRequestId(), r);
            }
            
            @Override
            public void onGetUserIdResponse(GetUserIdResponse r) {
                super.onGetUserIdResponse(r);
                mUserId = r.getUserId();
                invokeCallback(r.getRequestId(), r);
            }
            
            @Override
            public void onItemDataResponse(ItemDataResponse r) {
                super.onItemDataResponse(r);
                invokeCallback(r.getRequestId(), r);
            }
            
            @Override
            public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse r) {
                super.onPurchaseUpdatesResponse(r);
                invokeCallback(r.getRequestId(), r);
            }
        });
        PurchasingManager.initiateGetUserIdRequest();
    }
    
    static WeakReferenceHashTable<String, Callback> mCallbacks = new WeakReferenceHashTable<String, Callback>();
    
    public static void startPurchase(String productId, final Callback<PurchaseResponse> callback) {
        String id = PurchasingManager.initiatePurchaseRequest(productId);
        mCallbacks.put(id, callback);
    }
    
    public static void refreshPurchases(Callback<PurchaseUpdatesResponse> callback) {
        mCallbacks.put(PurchasingManager.initiatePurchaseUpdatesRequest(Offset.BEGINNING), callback);
    }
}
