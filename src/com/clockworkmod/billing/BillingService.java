package com.clockworkmod.billing;

import java.security.SecureRandom;
import java.util.ArrayList;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.vending.billing.Consts;
import com.android.vending.billing.Consts.ResponseCode;
import com.android.vending.billing.IMarketBillingService;

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
        Log.i(LOGTAG, signedData);
        Log.i(LOGTAG, signature);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = null;
        if (intent != null)
            action = intent.getAction();
        if (REFRESH_MARKET.equals(action)) {
            bindService(new Intent("com.android.vending.billing.MarketBillingService.BIND"), new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
                
                @Override
                public void onServiceConnected(ComponentName name, final IBinder service) {
                    try {
                        final IMarketBillingService s = IMarketBillingService.Stub.asInterface(service);
                        Bundle bundle = BillingReceiver.makeRequestBundle(BillingService.this, Consts.METHOD_RESTORE_TRANSACTIONS);
                        SecureRandom random = new SecureRandom();
                        bundle.putLong(Consts.BILLING_REQUEST_NONCE, random.nextLong());
                        s.sendBillingRequest(bundle);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, Context.BIND_AUTO_CREATE);
        }
        else if (Consts.ACTION_PURCHASE_STATE_CHANGED.equals(action)) {
            final ClockworkModBillingClient client = ClockworkModBillingClient.mInstance;
            if (client == null)
                return super.onStartCommand(intent, flags, startId);

            final String signedData = intent.getStringExtra(Consts.INAPP_SIGNED_DATA);
            final String signature = intent.getStringExtra(Consts.INAPP_SIGNATURE);

            bindService(new Intent("com.android.vending.billing.MarketBillingService.BIND"), new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
                
                @Override
                public void onServiceConnected(ComponentName name, final IBinder service) {
                    final ServiceConnection sc = this;
                    ThreadingRunnable.background(new ThreadingRunnable() {
                        @Override
                        public void run() {
                            try {
                                final IMarketBillingService s = IMarketBillingService.Stub.asInterface(service);
                                JSONObject purchase = new JSONObject(signedData);
                                JSONArray orders = purchase.getJSONArray("orders");
                                if (orders.length() == 0)
                                    return;
                                ArrayList<String> notificationIds = new ArrayList<String>();
                                for (int i = 0; i < orders.length(); i++) {
                                    JSONObject order = orders.getJSONObject(i);
                                    notificationIds.add(order.getString("notificationId"));
                                }
                                String[] nids = new String[orders.length()];
                                nids = notificationIds.toArray(nids);
                                reportAndroidPurchase(BillingService.this, signedData, signature);
                                Bundle bundle = BillingReceiver.makeRequestBundle(BillingService.this, Consts.METHOD_CONFIRM_NOTIFICATIONS);
                                bundle.putStringArray(Consts.BILLING_REQUEST_NOTIFY_IDS, nids);
                                s.sendBillingRequest(bundle);
                                unbindService(sc);
                                Intent intent = new Intent(BillingReceiver.SUCCEEDED);
                                sendBroadcast(intent);
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                                Intent intent = new Intent(BillingReceiver.FAILED);
                                sendBroadcast(intent);
                            }
                        }
                    });
                }
            }, Context.BIND_AUTO_CREATE);

        } else if (Consts.ACTION_NOTIFY.equals(action)) {
            final String notifyId = intent.getStringExtra(Consts.NOTIFICATION_ID);
            bindService(new Intent("com.android.vending.billing.MarketBillingService.BIND"), new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
                
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    try {
                        final IMarketBillingService s = IMarketBillingService.Stub.asInterface(service);
                        Bundle request = BillingReceiver.makeRequestBundle(BillingService.this, Consts.METHOD_GET_PURCHASE_INFORMATION);
                        SecureRandom random = new SecureRandom();
                        request.putLong(Consts.BILLING_REQUEST_NONCE, random.nextLong());
                        request.putStringArray(Consts.BILLING_REQUEST_NOTIFY_IDS, new String[] { notifyId });
                        s.sendBillingRequest(request);
                    }
                    catch (Exception e) {
                    }
                }
            }, Context.BIND_AUTO_CREATE);
        } else if (Consts.ACTION_RESPONSE_CODE.equals(action)) {
            long requestId = intent.getLongExtra(Consts.INAPP_REQUEST_ID, -1);
            int responseCodeIndex = intent.getIntExtra(Consts.INAPP_RESPONSE_CODE,
                    ResponseCode.RESULT_ERROR.ordinal());
        } else {
        }
        
        return super.onStartCommand(intent, flags, startId);
    }

}
