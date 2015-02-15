package com.clockworkmod.billing;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class BillingService extends Service {
    static String mSandboxPurchaseRequestId = null;
    static String mSandboxProductId = null;
    static String mSandboxBuyerId = null;
    static String REFRESH_MARKET = BillingReceiver.class.getName() + ".REFRESH_MARKET";
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private static final String LOGTAG = "ClockworkBilling";
    static void reportAndroidPurchase(final Context context, final String signedData, final String signature) throws Exception {
        Log.i(LOGTAG, "Reporting in app purchases...");
        HttpPost post = new HttpPost(String.format(ClockworkModBillingClient.INAPP_NOTIFY_URL, ClockworkModBillingClient.mInstance.mSellerId));
        ArrayList<BasicNameValuePair> pairs = new ArrayList<BasicNameValuePair>();
        pairs.add(new BasicNameValuePair("signed_data", signedData));
        pairs.add(new BasicNameValuePair("signature", signature));
        if (mSandboxPurchaseRequestId != null)
            pairs.add(new BasicNameValuePair("sandbox_purchase_request_id", mSandboxPurchaseRequestId));
        if (mSandboxProductId != null)
            pairs.add(new BasicNameValuePair("sandbox_product_id", mSandboxProductId));
        if (mSandboxBuyerId != null)
            pairs.add(new BasicNameValuePair("sandbox_buyer_id", mSandboxBuyerId));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(pairs);
        post.setEntity(entity);
        String result = StreamUtility.downloadUriAsString(post);
        Log.i(LOGTAG, result);
    }

    Handler mHandler = new Handler();
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = null;
        if (intent != null)
            action = intent.getAction();
        if (action != null) {
            Log.i(LOGTAG, action);
        }

        // after we have reported purchases, success or
        // failure, schedule this service to kill itself
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopSelf();
            }
        }, 5 * 60 * 1000);

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        if (REFRESH_MARKET.equals(action)) {
            bindService(serviceIntent, new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
                
                @Override
                public void onServiceConnected(ComponentName name, final IBinder service) {
                    try {
                        final IInAppBillingService s = IInAppBillingService.Stub.asInterface(service);
//                        Bundle bundle = BillingReceiver.makeRequestBundle(BillingService.this, Consts.METHOD_RESTORE_TRANSACTIONS);
//                        bundle.putLong(Consts.BILLING_REQUEST_NONCE, ClockworkModBillingClient.generateNonce(BillingService.this));
//                        s.sendBillingRequest(bundle);

                        final Bundle ownedItems = s.getPurchases(3, getPackageName(), "inapp", null);
                        ThreadingRunnable.background(new ThreadingRunnable() {
                            @Override
                            public void run() {
                                try {
                                    JSONArray orders = new JSONArray();
                                    int response = ownedItems.getInt("RESPONSE_CODE");
                                    if (response == 0) {
                                        ArrayList<String> ownedSkus =
                                        ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                                        ArrayList<String>  purchaseDataList =
                                        ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                                        ArrayList<String>  signatureList =
                                        ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                                        for (int i = 0; i < purchaseDataList.size(); i++) {
                                            String signedData = purchaseDataList.get(i);
                                            String signature = signatureList.get(i);
                                            reportAndroidPurchase(BillingService.this, signedData, signature);

                                            JSONObject order = new JSONObject(signedData);
                                            orders.put(order);
                                            JSONObject proof = new JSONObject();
                                            proof.put("signedData", signedData);
                                            proof.put("signature", signature);
                                            String proofString = proof.toString();
                                            String orderId = order.optString("orderId", null);
                                            if (orderId != null)
                                                ClockworkModBillingClient.getInstance().getOrderData().edit().putString(orderId, proofString).commit();
                                        }
                                    }
                                    Intent intent = new Intent(BillingReceiver.SUCCEEDED);
                                    intent.putExtra("orders", orders.toString());
                                    sendBroadcast(intent);
                                }
                                catch (Exception e) {
                                    Intent intent = new Intent(BillingReceiver.FAILED);
                                    sendBroadcast(intent);
                                    e.printStackTrace();
                                }

                            }
                        });

                        unbindService(this);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, Context.BIND_AUTO_CREATE);
        } else {
        }
        
        return super.onStartCommand(intent, flags, startId);
    }

}
