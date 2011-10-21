package com.clockworkmod.billing;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.EditText;

import com.android.vending.billing.IMarketBillingService;
import com.paypal.android.MEP.PayPal;
import com.paypal.android.MEP.PayPalInvoiceData;
import com.paypal.android.MEP.PayPalInvoiceItem;
import com.paypal.android.MEP.PayPalPayment;

public class ClockworkModBillingClient {
    static final String BASE_URL = "https://clockworkbilling.appspot.com";
    static final String API_URL = BASE_URL + "/api/v1";
    static final String ORDER_URL = API_URL + "/order/%s/%s?buyer_id=%s&custom_payload=%s&sandbox=%s";
    static final String INAPP_NOTIFY_URL = API_URL + "/notify/inapp/%s";
    static final String REDEEM_NOTIFY_URL = API_URL + "/notify/redeem/%s";
    static final String PURCHASE_URL = API_URL + "/purchase/%s/%s?nonce=%s&sandbox=%s";
    static final String TRANSFER_URL = API_URL + "/transfer/%s/%s?payer_email=%s&buyer_id=%s&sandbox=%s";

    static ClockworkModBillingClient mInstance;
    Context mContext;
    String mSellerId;
    String mClockworkPublicKey;
    String mMarketPublicKey;

    private ClockworkModBillingClient(Context context, final String sellerId, String clockworkPublicKey, String marketPublicKey, boolean sandbox) {
        mContext = context.getApplicationContext();
        mSandbox = sandbox;
        mSellerId = sellerId;
        mClockworkPublicKey = clockworkPublicKey;
        mMarketPublicKey = marketPublicKey;
    }
    
    static private void showAlertDialog(Context context, String s)
    {
        AlertDialog.Builder builder = new Builder(context);
        builder.setMessage(s);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.create().show();
    }

    private static Object mPayPalLock = new Object();
    
    private void invokeCallback(PurchaseCallback callback, PurchaseResult result) {
        if (result == PurchaseResult.SUCCEEDED)
            clearCachedPurchases();
        if (callback != null)
            callback.onFinished(result);
    }

    static private void invokeCallback(Context context, PurchaseCallback callback, PurchaseResult result) {
        if (result == PurchaseResult.SUCCEEDED)
            clearCachedPurchases(context);
        if (callback != null)
            callback.onFinished(result);
    }

    private void startPayPalPurchase(final Context context, final PurchaseCallback callback, final JSONObject payload) throws JSONException {
        final String sellerId = payload.getString("seller_id");
        final String sandboxEmail = payload.getString("paypal_sandbox_email");
        final double price = payload.getDouble("product_price");
        final String description = payload.getString("product_description");
        final String sellerName = payload.getString("seller_name");
        final String purchaseRequestId = payload.getString("purchase_request_id");
        final String name = payload.getString("product_name");
        final String paypalLiveAppId = payload.getString("paypal_live_app_id");
        final String paypalSandboxAppId = payload.getString("paypal_sandbox_app_id");
        final String paypalIPNUrl = payload.getString("paypal_ipn_url");

        final ProgressDialog dlg = new ProgressDialog(context);
        dlg.setMessage("Preparing PayPal order...");
        dlg.show();
        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                synchronized (mPayPalLock) {
                    try {
                        PayPal pp = PayPal.getInstance();
                        if (pp == null) {
                            String appId;
                            int environment;
                            if (mSandbox) {
                                environment = PayPal.ENV_SANDBOX;
                                appId = paypalSandboxAppId;
                            }
                            else {
                                environment = PayPal.ENV_LIVE;
                                appId = paypalLiveAppId;
                            }

                            PayPal.initWithAppID(context, appId, environment);
                            pp = PayPal.getInstance();
                            pp.setLanguage("en_US"); // Sets the language for the library.
                            pp.setShippingEnabled(false);
                            pp.setFeesPayer(PayPal.FEEPAYER_EACHRECEIVER); 
                            pp.setDynamicAmountCalculationEnabled(false);
                        }

                        foreground(new Runnable() {
                            @Override
                            public void run() {
                                dlg.dismiss();
                                PayPalPayment newPayment = new PayPalPayment();
                                newPayment.setIpnUrl(paypalIPNUrl);
                                newPayment.setCurrencyType("USD");
                                newPayment.setSubtotal(new BigDecimal(price));
                                newPayment.setPaymentType(PayPal.PAYMENT_TYPE_GOODS);
                                if (mSandbox) {
                                    newPayment.setRecipient(sandboxEmail);
                                }
                                else {
                                    newPayment.setRecipient(sellerId);
                                }
                                PayPalInvoiceData invoice = new PayPalInvoiceData();
                                invoice.setTax(new BigDecimal("0"));
                                invoice.setShipping(new BigDecimal("0"));
                                
                                PayPalInvoiceItem item1 = new PayPalInvoiceItem();
                                item1.setName(name);
                                item1.setID(purchaseRequestId);
                                item1.setTotalPrice(new BigDecimal(price));
                                item1.setUnitPrice(new BigDecimal(price));
                                item1.setQuantity(1);
                                invoice.getInvoiceItems().add(item1);

                                newPayment.setInvoiceData(invoice);
                                newPayment.setMerchantName(sellerName);
                                newPayment.setDescription(description);
                                newPayment.setCustomID(purchaseRequestId);
                                newPayment.setMemo(purchaseRequestId);

                                PayPal pp = PayPal.getInstance();
                                Intent checkoutIntent = pp.checkout(newPayment, context, new ResultDelegate());
                                BroadcastReceiver receiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        try {
                                            context.unregisterReceiver(this);
                                        }
                                        catch (Exception ex) {
                                        }

                                        PurchaseResult result;
                                        if (ResultDelegate.CANCELLED.equals(intent.getAction())) {
                                            result = PurchaseResult.CANCELLED;
                                        }
                                        else if (ResultDelegate.SUCCEEDED.equals(intent.getAction())) {
                                            result = PurchaseResult.SUCCEEDED;
                                        }
                                        else {
                                            result = PurchaseResult.FAILED;
                                        }
                                        invokeCallback(callback, result);
                                    }
                                };
                                
                                IntentFilter filter = new IntentFilter();
                                filter.addAction(ResultDelegate.SUCCEEDED);
                                filter.addAction(ResultDelegate.FAILED);
                                filter.addAction(ResultDelegate.CANCELLED);
                                context.registerReceiver(receiver, filter);
                                
                                context.startActivity(checkoutIntent);
                            }
                        });
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                        foreground(new Runnable() {
                            @Override
                            public void run() {
                                dlg.dismiss();
                                showAlertDialog(context, "There was an error processing your request. Please try again later!");
                            }
                        });
                    }
                }
            }
        });
    }
    
    public static void startUnmanagedInAppPurchase(final Context context, String productId, final PurchaseCallback callback) {
        startUnmanagedInAppPurchase(context, productId, null, callback);
    }

    public static void startUnmanagedInAppPurchase(final Context context, String productId, String developerPayload, final PurchaseCallback callback) {
        startInAppPurchaseInternal(context, productId, developerPayload, null, null, callback);
    }

    private static void startInAppPurchaseInternal(final Context context, final String productId, final String developerPayload, final String buyerId, final String purchaseRequestId, final PurchaseCallback callback) {
        new Runnable() {
            String mProductId = productId;

            @Override
            public void run() {
                
                final Runnable purchaseFlow = new Runnable() {
                    @Override
                    public void run() {
                        context.bindService(new Intent("com.android.vending.billing.MarketBillingService.BIND"), new ServiceConnection() {
                            @Override
                            public void onServiceDisconnected(ComponentName name) {
                            }
                            
                            @Override
                            public void onServiceConnected(ComponentName name, IBinder service) {
                                Bundle request = BillingReceiver.makeRequestBundle(context, Consts.METHOD_CHECK_BILLING_SUPPORTED);
                                final IMarketBillingService s = IMarketBillingService.Stub.asInterface(service);
                                try {
                                    Bundle result = s.sendBillingRequest(request);
                                    if (Consts.ResponseCode.valueOf(result.getInt(Consts.BILLING_RESPONSE_RESPONSE_CODE)) != Consts.ResponseCode.RESULT_OK)
                                        throw new Exception();
                                    request = BillingReceiver.makeRequestBundle(context, Consts.METHOD_REQUEST_PURCHASE);
                                    request.putString(Consts.BILLING_REQUEST_ITEM_ID, mProductId);
                                    if (developerPayload != null)
                                        request.putString(Consts.BILLING_REQUEST_DEVELOPER_PAYLOAD, developerPayload);
                                    Bundle response = s.sendBillingRequest(request);
                                    if (Consts.ResponseCode.valueOf(response.getInt(Consts.BILLING_RESPONSE_RESPONSE_CODE)) != Consts.ResponseCode.RESULT_OK)
                                        throw new Exception();
                                    PendingIntent pi = response.getParcelable(Consts.BILLING_RESPONSE_PURCHASE_INTENT);
                                    context.startIntentSender(pi.getIntentSender(), null, 0, 0, 0);
                                    context.unbindService(this);
                                    
                                    BroadcastReceiver receiver = new BroadcastReceiver() {
                                        @Override
                                        public void onReceive(Context context, Intent intent) {
                                            try {
                                                context.unregisterReceiver(this);
                                            }
                                            catch (Exception ex) {
                                            }

                                            PurchaseResult result;
                                            if (BillingReceiver.CANCELLED.equals(intent.getAction())) {
                                                result = PurchaseResult.CANCELLED;
                                            }
                                            else if (BillingReceiver.SUCCEEDED.equals(intent.getAction())) {
                                                result = PurchaseResult.SUCCEEDED;
                                            }
                                            else {
                                                result = PurchaseResult.FAILED;
                                            }
                                            invokeCallback(context, callback, result);
                                        }
                                    };
                                    
                                    IntentFilter filter = new IntentFilter();
                                    filter.addAction(BillingReceiver.SUCCEEDED);
                                    filter.addAction(BillingReceiver.CANCELLED);
                                    filter.addAction(BillingReceiver.FAILED);
                                    
                                    context.registerReceiver(receiver, filter);
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                    showAlertDialog(context, "There was an error processing your request. Please try again later!");
                                }
                            }
                        }, Context.BIND_AUTO_CREATE);   
                    }
                };
                
                ClockworkModBillingClient client = ClockworkModBillingClient.getInstance();
                if (client == null || !client.mSandbox) {
                    BillingService.mSandboxPurchaseRequestId = null;
                    BillingService.mSandboxProductId = null;
                    BillingService.mSandboxBuyerId = null;
                    purchaseFlow.run();
                    return;
                }
                
                AlertDialog.Builder builder = new Builder(context);
                builder.setTitle("Sandbox Purchase");
                final String[] results = new String[] { "android.test.purchased", "android.test.canceled", "android.test.refunded", "android.test.item_unavailable" };
                builder.setItems(results, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BillingService.mSandboxPurchaseRequestId = purchaseRequestId;
                        BillingService.mSandboxProductId = productId;
                        BillingService.mSandboxBuyerId = buyerId;
                        mProductId = results[which];
                        purchaseFlow.run();
                    }
                });
                builder.create().show();
            }
        }.run();

    }

    private void startAndroidPurchase(final Context context, final String buyerId, final PurchaseCallback callback, final JSONObject payload) throws NoSuchAlgorithmException, JSONException {
        final String purchaseRequestId = payload.optString("purchase_request_id", null);
        final String productId = payload.optString("product_id", null);
        startInAppPurchaseInternal(context, productId, purchaseRequestId, buyerId, purchaseRequestId, callback);
    }
    
    private void startRedeemCode(final Context context, final String buyerId, final PurchaseCallback callback, final JSONObject payload) throws NoSuchAlgorithmException, JSONException {
        final String purchaseRequestId = payload.optString("purchase_request_id", null);
        final String sellerId = payload.getString("seller_id");
        final EditText edit = new EditText(context);
        final String productId = payload.optString("product_id", null);
        edit.setHint("1234abcd");
        AlertDialog.Builder builder = new Builder(context);
        builder.setMessage("Enter Redeem Code");
        builder.setView(edit);
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                invokeCallback(callback, PurchaseResult.CANCELLED);
            }
        });
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ProgressDialog dlg = new ProgressDialog(context);
                dlg.setMessage("Redeeming...");
                final String code = edit.getText().toString();
                ThreadingRunnable.background(new ThreadingRunnable() {
                    @Override
                    public void run() {
                        try {
                            HttpPost post = new HttpPost(String.format(REDEEM_NOTIFY_URL, sellerId));
                            ArrayList<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
                            params.add(new BasicNameValuePair("product_id", productId));
                            params.add(new BasicNameValuePair("purchase_request_id", purchaseRequestId));
                            params.add(new BasicNameValuePair("code", code));
                            params.add(new BasicNameValuePair("sandbox", String.valueOf(mSandbox)));
                            post.setEntity(new UrlEncodedFormEntity(params));
                            final JSONObject redeemResult = StreamUtility.downloadUriAsJSONObject(post);
                            if (redeemResult.optBoolean("success", false)) {
                                foreground(new Runnable() {
                                    @Override
                                    public void run() {
                                        invokeCallback(callback, PurchaseResult.SUCCEEDED);
                                    }
                                });
                                return;
                            }

                            if (!redeemResult.optBoolean("is_redeemed", false)) {
                                throw new Exception();
                            }
                            
                            foreground(new Runnable() {
                                @Override
                                public void run() {
                                    AlertDialog.Builder builder = new Builder(context);
                                    builder.setMessage("This code has already been redeemed.");
                                    builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            invokeCallback(callback, PurchaseResult.FAILED);
                                        }
                                    });
                                    builder.create().show();
                                    builder.setCancelable(false);
                                }
                            });
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                            foreground(new Runnable() {
                                @Override
                                public void run() {
                                    AlertDialog.Builder builder = new Builder(context);
                                    builder.setMessage("Invalid redeem code.");
                                    builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            invokeCallback(callback, PurchaseResult.FAILED);
                                        }
                                    });
                                    builder.create().show();
                                    builder.setCancelable(false);
                                }
                            });
                        }
                    }
                });
            }
        });
        builder.create().show();
    }

    boolean mSandbox = true;
    public static ClockworkModBillingClient init(Context context, String sellerId, String clockworkPublicKey, String marketPublicKey, boolean sandbox) {
        if (mInstance != null) {
            //if (sandbox != mInstance.mSandbox)
            //    throw new Exception("ClockworkModBillingClient has already been initialized for a different environment.");
            return mInstance;
        }
        mInstance = new ClockworkModBillingClient(context, sellerId, clockworkPublicKey, marketPublicKey, sandbox);
        return mInstance;
    }

    public static ClockworkModBillingClient getInstance() {
        return mInstance;
    }
    
    SharedPreferences getCachedSettings() {
        return mContext.getApplicationContext().getSharedPreferences("billing-settings", Context.MODE_PRIVATE);
    }

    static SharedPreferences getOrderData(Context context) {
        return context.getSharedPreferences("order-data", Context.MODE_PRIVATE);
    }
    SharedPreferences getOrderData() {
        return getOrderData(mContext);
    }
    
    public void clearCachedPurchases() {
        clearCachedPurchases(mContext);
    }
    
    public static void clearCachedPurchases(Context context) {
        SharedPreferences orderData = getOrderData(context);
        Editor editor = orderData.edit();
        editor.clear();
        editor.commit();
    }
    
    static boolean checkSignature(String key, String responseData, String signature) {
        try {
            Signature sig = Signature.getInstance("SHA1withRSA");
            byte[] decodedKey = Base64.decode(key, 0);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            
            PublicKey pk = keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    
            sig.initVerify(pk);
            sig.update(responseData.getBytes());
            return sig.verify(Base64.decode(signature, 0));
        }
        catch (Exception ex) {
            return false;
        }
    }
    
    static long generateNonce(Context context) {
        long nonceDate = System.currentTimeMillis() / 1000L;
        String deviceId = getSafeDeviceId(context);
        byte[] bytes = deviceId.getBytes();
        BigInteger bigint = new BigInteger(bytes);
        long nonce = ((bigint.longValue() & 0xFFFFFFFF00000000L)) | nonceDate;
        return nonce;
    }
    
    static String getSafeDeviceId(Context context) {
        TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        String deviceId = tm.getDeviceId();
        if (deviceId == null) {
            deviceId = "000000000000";
        }
        String ret = digest(deviceId + context.getPackageName());
        return ret;
    }
    
    private static String digest(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return new BigInteger(1, md.digest(input.getBytes())).toString(16).toUpperCase();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static boolean checkNonce(Context context, long nonce, long cacheDuration) {
        String deviceId = getSafeDeviceId(context);
        byte[] bytes = deviceId.getBytes();
        BigInteger bigint = new BigInteger(bytes);
        long deviceNonce = bigint.longValue() & 0xFFFFFFFF00000000L;
        long maskedNonce = nonce & 0xFFFFFFFF00000000L;
        long dateNonce = nonce & 0x00000000FFFFFFFFL;
        dateNonce *= 1000L;
        if (cacheDuration != CACHE_DURATION_FOREVER && dateNonce + cacheDuration < System.currentTimeMillis())
            return false;
        return maskedNonce == deviceNonce;
    }

    public static final long CACHE_DURATION_FOREVER = Long.MAX_VALUE;

    private CheckPurchaseResult[] checkCachedPurchases(Context context, String productId, String buyerId, long marketCacheDuration, long billingCacheDuration, SharedPreferences orderData) {
        Editor edit = orderData.edit();
        CheckPurchaseResult[] result = new CheckPurchaseResult[3];
        // check the in app billing cache
        String proofString;
        if (!mSandbox) {
            // don't check the in app cache for sandbox, as that is always production data.
            try {
                proofString = orderData.getString(productId, null);
                if (proofString == null)
                    throw new Exception();
                JSONObject proof = new JSONObject(proofString);
                Log.i(LOGTAG, proof.toString(4));
                String signedData = proof.getString("signedData");
                String signature = proof.getString("signature");
                if (!checkSignature(mMarketPublicKey, signedData, signature))
                    throw new Exception();

                proof = new JSONObject(signedData);
                // TODO: change this to long in the future. Earlier versions of the in app billing api would
                // mask it to an int.
                long nonce = proof.getLong("nonce");
                // the nonce check also checks against the device id.
                if (!checkNonce(context, nonce, marketCacheDuration))
                    throw new Exception();
                JSONArray orders = proof.getJSONArray("orders");
                for (int i = 0; i < orders.length(); i++) {
                    JSONObject order = orders.getJSONObject(i);
                    if (productId.equals(order.getString("productId")) && context.getPackageName().equals(order.optString("packageName", null))) {
                        Log.i(LOGTAG, "Cached in app billing success");
                        result[0] = result[1] = CheckPurchaseResult.purchased(new InAppOrder(order));
                        return result;
                    }
                }
                result[1] = CheckPurchaseResult.notPurchased();
            }
            catch (Exception ex) {
                if (ex.getClass() != Exception.class)
                    ex.printStackTrace();
                result[1] = CheckPurchaseResult.stale();
                edit.remove(productId);
                edit.commit();
            }
        }

        try
        {
            proofString = orderData.getString("server-purchases", null);
            if (proofString == null)
                throw new Exception();

            JSONObject proof = new JSONObject(proofString);
            Log.i(LOGTAG, proof.toString(4));
            String signedData = proof.getString("signed_data");
            String signature = proof.getString("signature");
            if (!checkSignature(mClockworkPublicKey, signedData, signature))
                throw new Exception();

            proof = new JSONObject(signedData);
            if (proof.optBoolean("sandbox", true) != mSandbox)
                throw new Exception();
            String sellerId = proof.optString("seller_id", null);
            if (!mSellerId.equals(sellerId))
                throw new Exception();
            long timestamp = proof.getLong("timestamp");
            // no need to check the nonce as done above,
            // checking the returned timestamp and buyer_id is good enough
            if (billingCacheDuration != CACHE_DURATION_FOREVER && timestamp < System.currentTimeMillis() - billingCacheDuration)
                throw new Exception();
            if (!buyerId.equals(proof.getString("buyer_id")))
                throw new Exception();
            JSONArray orders = proof.getJSONArray("orders");
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.getJSONObject(i);
                if (productId.equals(order.getString("product_id"))) {
                    Log.i(LOGTAG, "Cached server billing success");
                    result[0] = result[2] = CheckPurchaseResult.purchased(new ClockworkOrder(order));
                    return result;
                }
            }
            result[2] = CheckPurchaseResult.notPurchased();
        }
        catch (Exception ex) {
            if (ex.getClass() != Exception.class)
                ex.printStackTrace();
            result[2] = CheckPurchaseResult.stale();
            edit.remove("server-purchases");
            edit.commit();
        }

        if (result[1] == CheckPurchaseResult.notPurchased() && result[2] == CheckPurchaseResult.notPurchased())
            result[0] = CheckPurchaseResult.notPurchased();
        else
            result[0] = CheckPurchaseResult.stale();
        return result;
    }
    
    private class CheckPurchaseState {
        public boolean restoredMarket = false;
        public boolean refreshedServer = false;
        public CheckPurchaseResult serverResult = CheckPurchaseResult.error();
        public CheckPurchaseResult marketResult = CheckPurchaseResult.error();
        public boolean reportedPurchase = false;
    }
    
    private static final String LOGTAG = "ClockworkModBilling";

    public CheckPurchaseResult checkPurchase(final Context context, final String productId, final String buyerId, final long cacheDuration, final CheckPurchaseCallback callback) {
        return checkPurchase(context, productId, buyerId, cacheDuration, cacheDuration, callback);
    }

    public CheckPurchaseResult checkPurchase(final Context context, final String productId, final String buyerId, final long marketCacheDuration, final long billingCacheDuration, final CheckPurchaseCallback callback) {
        final CheckPurchaseState state = new CheckPurchaseState();
        final Handler handler = new Handler();
        final Runnable reportPurchase = new Runnable() {
            @Override
            public void run() {
                // report if both system of records have reported back, or one indicates success
                if ((state.refreshedServer && state.restoredMarket) || state.marketResult.isPurchased() || state.serverResult.isPurchased()) {
                    // prevent double reporting
                    if (!state.reportedPurchase) {
                        state.reportedPurchase = true;
                        if (callback == null)
                            return;
                        if (state.marketResult.isPurchased())
                            callback.onFinished(state.marketResult);
                        else if (state.serverResult.isPurchased())
                            callback.onFinished(state.serverResult);
                        else if (state.marketResult.isError())
                            callback.onFinished(state.marketResult);
                        else if (state.serverResult.isError())
                            callback.onFinished(state.serverResult);
                        else
                            callback.onFinished(CheckPurchaseResult.notPurchased());
                    }
                }
            }
        };

        final SharedPreferences orderData = getOrderData();
        // first check the cache
        CheckPurchaseResult[] cachedResults = checkCachedPurchases(context, productId, buyerId, marketCacheDuration, billingCacheDuration, orderData);
        CheckPurchaseResult cachedResult = cachedResults[0];
        CheckPurchaseResult _syncResult = null;
        if (cachedResult.isPurchased())
            _syncResult = cachedResult;
        final CheckPurchaseResult syncResult = _syncResult;
        if (syncResult != null) {
            // only refresh the cache if it is stale
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onFinished(syncResult);
                    }
                }
            });
            Log.i(LOGTAG, "CheckPurchase result: " + syncResult.toString());
            return syncResult;
        }

        // don't do a market payment refresh for the sandbox, as that only returns production data.
        if (!mSandbox && cachedResults[1].isStale()) {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        context.unregisterReceiver(this);
                    }
                    catch (Exception ex) {
                    }

                    state.refreshedServer = true;
                    if (BillingReceiver.CANCELLED.equals(intent.getAction())) {
                        state.marketResult = CheckPurchaseResult.notPurchased();
                    }
                    else if (BillingReceiver.SUCCEEDED.equals(intent.getAction())) {
                        CheckPurchaseResult[] cachedResults = checkCachedPurchases(context, productId, buyerId, CACHE_DURATION_FOREVER, 0, orderData);
                        CheckPurchaseResult cachedResult = cachedResults[0];
                        if (cachedResult.isPurchased())
                            state.serverResult = cachedResult;
                        else
                            state.serverResult = CheckPurchaseResult.notPurchased();
                    }
                    else {
                        state.marketResult = CheckPurchaseResult.notPurchased();
                    }
                    Log.i(LOGTAG, "In app billing result: " + state.marketResult);
                    state.restoredMarket = true;
                    reportPurchase.run();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(BillingReceiver.SUCCEEDED);
            filter.addAction(BillingReceiver.CANCELLED);
            filter.addAction(BillingReceiver.FAILED);
            context.registerReceiver(receiver, filter);
            
            refreshMarketPurchases();
        }
        else {
            state.restoredMarket = true;
            state.marketResult = CheckPurchaseResult.notPurchased();
        }

        if (cachedResults[2].isStale()) {
            SharedPreferences settings = getCachedSettings();
            final String authToken = settings.getString("authToken", null);
            // refresh the server purchases
            ThreadingRunnable.background(new ThreadingRunnable() {
                @Override
                public void run() {
                    try {
                        String purchaseUrl = String.format(PURCHASE_URL, mSellerId, buyerId, generateNonce(context), mSandbox);
                        HttpGet get = new HttpGet(purchaseUrl);
                        
                        if (authToken != null) {
                            try {
                                String cookie = getCookie(authToken);
                                addAuthentication(get, cookie);
                            }
                            catch (Exception ex) {
                            }
                        }
                        
                        JSONObject purchases = StreamUtility.downloadUriAsJSONObject(get);
                        Editor editor = orderData.edit();
                        editor.putString("server-purchases", purchases.toString());
                        editor.commit();

                        CheckPurchaseResult[] cachedResults = checkCachedPurchases(context, productId, buyerId, 0, CACHE_DURATION_FOREVER, orderData);
                        CheckPurchaseResult cachedResult = cachedResults[0];
                        if (cachedResult.isPurchased())
                            state.serverResult = cachedResult;
                        else
                            state.serverResult = CheckPurchaseResult.notPurchased();
                        Log.i(LOGTAG, "Server billing result: " + state.serverResult.mState);
                    }
                    catch (Exception ex) {
                        state.serverResult = CheckPurchaseResult.error();
                    }
                    finally {
                        state.refreshedServer = true;
                        foreground(new Runnable() {
                            @Override
                            public void run() {
                                reportPurchase.run();
                            }
                        });
                    }
                }
            });
        }
        else {
            state.refreshedServer = true;
            state.serverResult = CheckPurchaseResult.notPurchased();
        }

        // force a timeout, and report an error
        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            state.restoredMarket = true;
                            state.refreshedServer = true;
                            reportPurchase.run();
                        }
                    });
                }
                catch (Exception ex) {
                }
            }
        });
        
        return CheckPurchaseResult.pending();
    }
    
    public Intent getRecoverPurchasesActivityIntent(Context context, String productId, String buyerId) {
        Intent intent = new Intent(context, BillingActivity.class);
        intent.putExtra("action", "recover");
        intent.putExtra("productId", productId);
        intent.putExtra("buyerId", buyerId);
        return intent;
    }

    public void refreshMarketPurchases() {
        Log.i(LOGTAG, "Refreshing Market purchases...");
        Intent i = new Intent(BillingService.REFRESH_MARKET);
        i.setClass(mContext, BillingService.class);
        mContext.startService(i);
    }

    public void startPurchase(final Context context, final String productId, LinkPurchase linkOption, boolean allowCachedEmail, final PurchaseCallback callback) {
        if (linkOption == null)
            throw new NullPointerException("linkOption");
        startPurchase(context, productId, null, linkOption, allowCachedEmail, null, "", PurchaseType.ANY, callback);
    }
    
    // default super easy to use implementation that prompts for nothing besides the payment method
    public void startPurchase(final Context context, final String productId, final PurchaseCallback callback) {
        startPurchase(context, productId, null, LinkPurchase.NO_PROMPT, false, null, "", PurchaseType.ANY, callback);
    }

    public void startPurchase(final Context context, final String productId, String customPayload, final PurchaseCallback callback) {
        if (customPayload == null)
            throw new NullPointerException("customPayload");
        startPurchase(context, productId, null, LinkPurchase.NO_PROMPT, false, null, customPayload, PurchaseType.ANY, callback);
    }

    public void startPurchase(final Context context, final String productId, String customPayload, PurchaseType type, final PurchaseCallback callback) {
        if (customPayload == null)
            throw new NullPointerException("customPayload");
        startPurchase(context, productId, null, LinkPurchase.NO_PROMPT, false, null, customPayload, type, callback);
    }

    public void startPurchase(final Context context, final String productId, String buyerId, String customPayload, PurchaseType type, final PurchaseCallback callback) {
        if (customPayload == null)
            throw new NullPointerException("customPayload");
        if (buyerId == null)
            throw new NullPointerException("buyerId");
        startPurchase(context, productId, buyerId, LinkPurchase.NO_PROMPT, false, null, customPayload, type, callback);
    }

    public void startPurchase(final Context context, final String productId, LinkPurchase linkOption, boolean allowCachedEmail, final String customPayload, final PurchaseType type, final PurchaseCallback callback) {
        if (linkOption == null)
            throw new NullPointerException("linkOption");
        startPurchase(context, productId, null, linkOption, allowCachedEmail, null, customPayload, type, callback);
    }

    public void startPurchase(final Context context, final String productId, String buyerId, LinkPurchase linkOption, boolean allowCachedEmail, final String customPayload, final PurchaseType type, final PurchaseCallback callback) {
        if (linkOption == null)
            throw new NullPointerException("linkOption");
        if (buyerId == null)
            throw new NullPointerException("buyerId");
        startPurchase(context, productId, buyerId, linkOption, allowCachedEmail, null, customPayload, type, callback);
    }
    
    public void startPurchase(final Context context, final String productId, String buyerId, final String buyerEmail, final String customPayload, final PurchaseType type, final PurchaseCallback callback) {
        if (buyerEmail == null)
            throw new NullPointerException("buyerEmail");
        // buyer email may be invalid and will not be validated, just trust the consumer of the API to not mess this up
        startPurchase(context, productId, buyerId, LinkPurchase.NO_PROMPT, false, buyerEmail, customPayload, type, callback);
    }
    
    // should we move this into an activity?
    private void startPurchaseInternal(final Context context, final String productId, final String buyerId, final String buyerEmail, final String customPayload, final PurchaseType type, final PurchaseCallback callback) {
        // everything is ready to go, initiate the purchase
        final ProgressDialog dlg = new ProgressDialog(context);
        dlg.setMessage("Preparing order...");
        dlg.show();
        String _url = String.format(ORDER_URL, mSellerId, productId, Uri.encode(buyerId), Uri.encode(customPayload), mSandbox);
        if (buyerEmail != null)
            _url = _url + "&buyer_email=" + Uri.encode(buyerEmail);
        final String url = _url;
        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                try {
                    final JSONObject payload = StreamUtility.downloadUriAsJSONObject(url);
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            dlg.dismiss();

                            if (payload.optBoolean("purchased", false)) {
                                if (!mSandbox || type != PurchaseType.MARKET_INAPP) {
                                    AlertDialog.Builder builder = new Builder(context);
                                    builder.setMessage("This product has already been purchased.");
                                    builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // even though the purchase failed, it has already been purchased
                                            // so let's consider it a success.
                                            invokeCallback(callback, PurchaseResult.SUCCEEDED);
                                        }
                                    });
                                    builder.create().show();
                                    builder.setCancelable(false);
                                    return;
                                }
                            }
                            try {
                                if (type == PurchaseType.PAYPAL) {
                                    startPayPalPurchase(context, callback, payload);
                                }
                                else if (type == PurchaseType.MARKET_INAPP) {
                                    startAndroidPurchase(context, buyerId, callback, payload);
                                }
                                else if (type == PurchaseType.REDEEM) {
                                    startRedeemCode(context, buyerId, callback, payload);
                                }
                                else {
                                    // dead code?
                                    throw new Exception("No purchase type provided?");
                                }
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                                showAlertDialog(context, "There was an error processing your request. Please try again later!");
                            }
                        }
                    });
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            dlg.dismiss();
                            // TODO: Localize...
                            showAlertDialog(context, "There was an error processing your request. Please try again later!");
                        }
                    });
                }
            }
        });
    }

    // should we move this into an activity?
    private void startPurchase(final Context context, final String productId, String _buyerId, final LinkPurchase linkOption, final boolean allowCachedEmail, final String buyerEmail, final String customPayload, final PurchaseType type, final PurchaseCallback callback) {
        if (type == null)
            throw new NullPointerException("type");
        if (_buyerId == null)
            _buyerId = getSafeDeviceId(context);
        final String buyerId = _buyerId;

        if (type == PurchaseType.ANY) {
            AlertDialog.Builder builder = new Builder(context);
            builder.setItems(new String[] { "PayPal", "Android Market", "Redeem Code" }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        if (which == 0) {
                            startPurchase(context, productId, buyerId, linkOption, allowCachedEmail, buyerEmail, customPayload, PurchaseType.PAYPAL, callback);
                        }
                        else if (which == 1) {
                            startPurchase(context, productId, buyerId, linkOption, allowCachedEmail, buyerEmail, customPayload, PurchaseType.MARKET_INAPP, callback);
                        }
                        else if (which == 2) {
                            startPurchase(context, productId, buyerId, linkOption, allowCachedEmail, buyerEmail, customPayload, PurchaseType.REDEEM, callback);
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                        showAlertDialog(context, "There was an error processing your request. Please try again later!");
                    }
                }
            });
            builder.create().show();
            return;
        }
        
        // at this point, we definitely have a purchase type in mind

        if (buyerEmail != null) {
            // live with whatever buyer email we have
            startPurchaseInternal(context, productId, buyerId, buyerEmail, customPayload, type, callback);
            return;
        }
        
        if (allowCachedEmail) {
            // see if we have a cached version
            SharedPreferences cachedSettings = getCachedSettings();
            String cachedBuyerEmail = cachedSettings.getString("buyerEmail", null);
            if (cachedBuyerEmail != null) {
                startPurchaseInternal(context, productId, buyerId, cachedBuyerEmail, customPayload, type, callback);
                return;
            }
        }

        // at this point we MUST prompt to get the email, see if our flags allow this
        if (linkOption == LinkPurchase.NO_PROMPT) {
            // not prompting
            startPurchaseInternal(context, productId, buyerId, null, customPayload, type, callback);
            return;
        }
        
        if (type == PurchaseType.MARKET_INAPP && linkOption == LinkPurchase.PROMPT_EMAIL) {
            // not prompting for market purchases, as it is already linked to a google account.
            // there is a specific option, PROMPT_EMAIL_INCLUDING_MARKET, for that.
            startPurchaseInternal(context, productId, buyerId, null, customPayload, type, callback);
            return;
        }
        
        // let's prompt for a buyer email
        AccountManager amgr = AccountManager.get(context);
        final Account[] accounts = amgr.getAccountsByType("com.google");

        if (accounts.length == 0) {
            startPurchaseInternal(context, productId, buyerId, null, customPayload, type, callback);
            return;
        }

        String[] accountOptions;
        if (linkOption != LinkPurchase.REQUIRE_EMAIL) {
            accountOptions = new String[accounts.length + 1];
            accountOptions[accounts.length] = "Do Not Link";
        }
        else {
            accountOptions = new String[accounts.length];
        }

        for (int i = 0; i < accounts.length; i++) {
            Account account = accounts[i];
            accountOptions[i] = account.name;
        }

        AlertDialog.Builder builder = new Builder(context);
        builder.setItems(accountOptions, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == accounts.length) {
                    startPurchaseInternal(context, productId, buyerId, null, customPayload, type, callback);
                    return;
                }

                // don't actually need an auth token or anything, just let them select their email...
                startPurchaseInternal(context, productId, buyerId, accounts[which].name, customPayload, type, callback);
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                invokeCallback(callback, PurchaseResult.CANCELLED);
            }
        });
        
        builder.setTitle("Link To Account");
        //builder.setMessage("Link your purchase to a Google account to use it on multiple phones.");
        builder.create().show();
    }

    static void addAuthentication(HttpMessage message, String ascidCookie) {
        message.setHeader("Cookie", ascidCookie);
        message.setHeader("X-Same-Domain", "1"); // XSRF
    }

    static String getCookie(final String authToken) throws ClientProtocolException, IOException, URISyntaxException {
        if (authToken == null)
            return null;
        Log.i(LOGTAG, authToken);
        Log.i(LOGTAG, "getting cookie");
        // Get ACSID cookie
        DefaultHttpClient client = new DefaultHttpClient();
        URI uri = new URI("https://clockworkbilling.appspot.com/_ah/login?continue=" + URLEncoder.encode("http://localhost", "UTF-8") + "&auth=" + authToken);
        HttpGet method = new HttpGet(uri);
        final HttpParams getParams = new BasicHttpParams();
        HttpClientParams.setRedirecting(getParams, false); // continue is not
                                                           // used
        method.setParams(getParams);

        HttpResponse res = client.execute(method);
        Header[] headers = res.getHeaders("Set-Cookie");
        if (res.getStatusLine().getStatusCode() != 302 || headers.length == 0) {
            //throw new Exception("failure getting cookie: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine().getReasonPhrase());
            return null;
        }

        String ascidCookie = null;
        for (Header header : headers) {
            if (header.getValue().indexOf("ACSID=") >= 0) {
                // let's parse it
                String value = header.getValue();
                String[] pairs = value.split(";");
                ascidCookie = pairs[0];
            }
        }
        return ascidCookie;
    }

}
