package com.clockworkmod.billing;

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
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.EditText;

import com.android.vending.billing.Consts;
import com.android.vending.billing.IMarketBillingService;
import com.paypal.android.MEP.PayPal;
import com.paypal.android.MEP.PayPalInvoiceData;
import com.paypal.android.MEP.PayPalInvoiceItem;
import com.paypal.android.MEP.PayPalPayment;

public class ClockworkModBillingClient {
    static final String BASE_URL = "https://2.clockworkbilling.appspot.com";
    static final String API_URL = BASE_URL + "/api/v1";
    static final String ORDER_URL = API_URL + "/order/%s/%s?buyer_id=%s&custom_payload=%s&sandbox=%s";
    static final String INAPP_NOTIFY_URL = API_URL + "/notify/inapp/%s";
    static final String REDEEM_NOTIFY_URL = API_URL + "/notify/redeem/%s";

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

    public void beginPayPalPurchase(final Context context, final PurchaseCallback callback, final JSONObject payload) throws JSONException {
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

    public void beginAndroidPurchase(final Context context, final String productId, final String buyerId, final PurchaseCallback callback, final JSONObject payload) throws NoSuchAlgorithmException, JSONException {
        final String purchaseRequestId = payload.optString("purchase_request_id", null);
        
        new Runnable() {
            String mProductId = payload.optString("product_id", null);
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
                                    request.putString(Consts.BILLING_REQUEST_DEVELOPER_PAYLOAD, purchaseRequestId);
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
                                            System.out.println("received");
                                            if (callback != null)
                                                callback.onFinished(result);
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

                if (!mSandbox) {
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
    
    public void beginRedeemCode(final Context context, final String productId, final String buyerId, final PurchaseCallback callback, final JSONObject payload) throws NoSuchAlgorithmException, JSONException {
        final String purchaseRequestId = payload.optString("purchase_request_id", null);
        final String sellerId = payload.getString("seller_id");
        final EditText edit = new EditText(context);
        edit.setHint("1234abcd");
        AlertDialog.Builder builder = new Builder(context);
        builder.setMessage("Enter Redeem Code");
        builder.setView(edit);
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
                                        callback.onFinished(PurchaseResult.SUCCEEDED);
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
                                            if (callback != null)
                                                callback.onFinished(PurchaseResult.FAILED);
                                        }
                                    });
                                    builder.create().show();
                                    builder.setCancelable(false);
                                }
                            });
                        }
                        catch (Exception ex) {
                            foreground(new Runnable() {
                                @Override
                                public void run() {
                                    AlertDialog.Builder builder = new Builder(context);
                                    builder.setMessage("Invalid redeem code.");
                                    builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (callback != null)
                                                callback.onFinished(PurchaseResult.FAILED);
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

    private void showPaymentOptions(final Context context, final String productId, final String buyerId, final PurchaseCallback callback, final JSONObject payload) {
        AlertDialog.Builder builder = new Builder(context);
        builder.setItems(new String[] { "PayPal", "Android Market", "Redeem Code" }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    if (which == 0) {
                        beginPayPalPurchase(context, callback, payload);
                    }
                    else if (which == 1) {
                        beginAndroidPurchase(context, productId, buyerId, callback, payload);
                    }
                    else if (which == 2) {
                        beginRedeemCode(context, productId, buyerId, callback, payload);
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
        mInstance = new ClockworkModBillingClient(context, sellerId, sandbox);
        try {
            if (!sandbox) {
                mInstance.refreshMarketPurchases();
            }
        }
        catch (Exception ex) {
        }
        return mInstance;
    }
    
    public void refreshMarketPurchases() {
        Intent i = new Intent(BillingService.REFRESH_MARKET);
        i.setClass(mContext, BillingService.class);
        mContext.startService(i);
    }
    
    public static final int TYPE_PAYPAL = 0;
    public static final int TYPE_MARKET = 1;
    public static final int TYPE_REDEEM = 2;

    public void startPurchase(final Context context, final String productId, final String buyerId, final String customPayload, final PurchaseCallback callback) {
        startPurchase(context, productId, buyerId, customPayload, callback, -1);
    }

    public void startPurchase(final Context context, final String productId, final String buyerId, final String customPayload, final PurchaseCallback callback, final int type) {
        final ProgressDialog dlg = new ProgressDialog(context);
        dlg.setMessage("Preparing order...");
        dlg.show();
        final String url = String.format(ORDER_URL, mSellerId, productId, Uri.encode(buyerId), Uri.encode(customPayload), mSandbox);
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
                                if (!mSandbox || type != TYPE_MARKET) {
                                    AlertDialog.Builder builder = new Builder(context);
                                    builder.setMessage("This product has already been purchased.");
                                    builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (callback != null)
                                                callback.onFinished(PurchaseResult.FAILED);
                                        }
                                    });
                                    builder.create().show();
                                    builder.setCancelable(false);
                                    return;
                                }
                            }
                            try {
                                switch (type) {
                                case 0:
                                    beginPayPalPurchase(context, callback, payload);
                                    break;
                                case 1:
                                    beginAndroidPurchase(context, productId, buyerId, callback, payload);
                                    break;
                                case 2:
                                    beginRedeemCode(context, productId, buyerId, callback, payload);
                                    break;
                                default:
                                    showPaymentOptions(context, productId, buyerId, callback, payload);
                                    break;
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
    
}
