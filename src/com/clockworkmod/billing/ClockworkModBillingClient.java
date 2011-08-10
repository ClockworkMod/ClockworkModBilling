package com.clockworkmod.billing;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.android.vending.billing.Consts;
import com.android.vending.billing.IMarketBillingService;
import com.paypal.android.MEP.PayPal;
import com.paypal.android.MEP.PayPalInvoiceData;
import com.paypal.android.MEP.PayPalInvoiceItem;
import com.paypal.android.MEP.PayPalPayment;

public class ClockworkModBillingClient {
    static final String BASE_URL = "https://clockworkbilling.appspot.com";
    static final String API_URL = BASE_URL + "/api/v1";
    static final String INAPP_REQUEST_URL = API_URL + "/request/inapp/%s/%s?buyer_id=%s&custom_payload=%s&sandbox=%s";
    static final String INAPP_NOTIFY_URL = API_URL + "/notify/inapp/%s";

    static ClockworkModBillingClient mInstance;
    Context mContext;
    String mSellerId;
    
    private ClockworkModBillingClient(Context context, final String sellerId, boolean sandbox) {
        mContext = context.getApplicationContext();
        mSandbox = sandbox;
        mSellerId = sellerId;
        Consts.DEBUG = sandbox;
    }
    
    static private void showAlertDialog(Context context, int stringResource)
    {
        showAlertDialog(context, context.getString(stringResource));
    }
    
    static private void showAlertDialog(Context context, String s)
    {
        AlertDialog.Builder builder = new Builder(context);
        builder.setMessage(s);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.create().show();
    }
    
    private static Object mPayPalLock = new Object();
    
    private void beginPayPalPurchase(final Context context, final PurchaseCallback callback, final JSONObject payload) throws JSONException {
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
                                        System.out.println("received");
                                        if (callback != null)
                                            callback.onFinished(result);
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

    private void beginAndroidPurchase(final Context context, final PurchaseCallback callback, final JSONObject payload) throws NoSuchAlgorithmException, JSONException {
        final String purchaseRequestId = payload.getString("purchase_request_id");
        
        new Runnable() {
            String productId = payload.getString("product_id");
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
                                    request.putString(Consts.BILLING_REQUEST_ITEM_ID, productId);
                                    request.putString(Consts.BILLING_REQUEST_DEVELOPER_PAYLOAD, purchaseRequestId);
                                    Bundle response = s.sendBillingRequest(request);
                                    if (Consts.ResponseCode.valueOf(response.getInt(Consts.BILLING_RESPONSE_RESPONSE_CODE)) != Consts.ResponseCode.RESULT_OK)
                                        throw new Exception();
                                    PendingIntent pi = response.getParcelable(Consts.BILLING_RESPONSE_PURCHASE_INTENT);
                                    Method m = null;
                                    try {
                                        m = context.getClass().getMethod("startIntentSender", IntentSender.class, Intent.class, int.class, int.class, int.class);
                                    }
                                    catch (Exception ex) {
                                    }
                                    if (m != null)
                                        m.invoke(context, pi.getIntentSender(), null, 0, 0, 0);
                                    else
                                        pi.send(context, 0, new Intent());
                                    
                                    final ServiceConnection sc = this;
                                    
                                    BroadcastReceiver receiver = new BroadcastReceiver() {
                                        String mNotifyId = null;
                                        @Override
                                        public void onReceive(Context context, Intent intent) {
                                            try {
                                                if (BillingReceiver.IN_APP_NOTIFY.equals(intent.getAction())) {
                                                    mNotifyId = intent.getStringExtra(Consts.NOTIFICATION_ID);
                                                    Bundle request = BillingReceiver.makeRequestBundle(context, Consts.METHOD_GET_PURCHASE_INFORMATION);
                                                    request.putStringArray(Consts.BILLING_REQUEST_NOTIFY_IDS, new String[] { mNotifyId });
                                                    s.sendBillingRequest(request);
                                                }
                                                else if (BillingReceiver.PURCHASE_STATE_CHANGED.equals(intent.getAction())) {
                                                    if (mNotifyId == null)
                                                        return;
                                                    
                                                    context.unbindService(sc);
                                                }
                                            }
                                            catch (Exception ex) {
                                                ex.printStackTrace();
                                                showAlertDialog(context, "There was an error processing your request. Please try again later!");
                                            }
                                        }
                                    };
                                    
                                    IntentFilter filter = new IntentFilter();
                                    filter.addAction(BillingReceiver.IN_APP_NOTIFY);
                                    filter.addAction(BillingReceiver.PURCHASE_STATE_CHANGED);
                                    
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

                if (!mSandbox) {
                    BillingService.mSandboxPurchaseRequestId = null;
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
                        productId = results[which];
                        purchaseFlow.run();
                    }
                });
                builder.create().show();
            }
        }.run();
    }
    
    private void showPaymentOptions(final Context context, final PurchaseCallback callback, final JSONObject payload) {
        AlertDialog.Builder builder = new Builder(context);
        builder.setItems(new String[] { "PayPal", "Android Market" }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    if (which == 0) {
                        beginPayPalPurchase(context, callback, payload);
                    }
                    else if (which == 1) {
                        beginAndroidPurchase(context, callback, payload);
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    showAlertDialog(context, "There was an error processing your request. Please try again later!");
                }
            }
        });
        builder.create().show();
    }

    boolean mSandbox = true;
    public static ClockworkModBillingClient getInstance(Context context, String sellerId, boolean sandbox) {
        if (mInstance != null) {
            //if (sandbox != mInstance.mSandbox)
            //    throw new Exception("ClockworkModBillingClient has already been initialized for a different environment.");
            return mInstance;
        }
        return mInstance = new ClockworkModBillingClient(context, sellerId, sandbox);
    }

    public void startPurchase(final Context context, final String productId, final String buyerId, final String customPayload, final PurchaseCallback callback) {
        final ProgressDialog dlg = new ProgressDialog(context);
        dlg.setMessage("Preparing order...");
        dlg.show();
        final String url = String.format(INAPP_REQUEST_URL, mSellerId, productId, Uri.encode(buyerId), Uri.encode(customPayload), mSandbox);
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
                                showAlertDialog(context, "This product has already been purchased.");
                                if (callback != null)
                                    callback.onFinished(PurchaseResult.FAILED);
                                return;
                            }
                            showPaymentOptions(context, callback, payload);
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
    
}
