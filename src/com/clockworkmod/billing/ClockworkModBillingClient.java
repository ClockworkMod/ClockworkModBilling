package com.clockworkmod.billing;

import org.json.JSONObject;

import com.paypal.android.MEP.PayPal;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;

public class ClockworkModBillingClient {
    private static String BASE_URL = "https://clockworkbilling.appspot.com";
    private static String API_URL = BASE_URL + "/api/v1";
    private static String PURCHASE_URL = API_URL + "/purchase/%s/%s";
    
    private static ClockworkModBillingClient mInstance;
    Context mContext;
    
    private ClockworkModBillingClient(Context context) {
        mContext = context.getApplicationContext();
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
    
    static private void beginPayPalPurchase(final Context context, final String sellerId, final String productId, final JSONObject payload) {
        ThreadingRunnable.background(new ThreadingRunnable() {
            PayPal mPayPal;
            @Override
            public void run() {
                mPayPal = PayPal.getInstance();
                foreground(new Runnable() {
                    @Override
                    public void run() {
                    }
                });
            }
        });
    }
    
    static private void showPaymentOptions(final Context context, final String sellerId, final String productId) {
        AlertDialog.Builder builder = new Builder(context);
        builder.setItems(new String[] { "PayPal", "Android Market" }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    
                }
            }
        });
    }
    
    
    public static ClockworkModBillingClient getInstance(Context context) {
        if (mInstance != null)
            return mInstance;
        return mInstance = new ClockworkModBillingClient(context);
    }
    
    public void startPurchase(final Context context, final String sellerId, final String productId) {
        final String url = String.format(PURCHASE_URL, sellerId, productId);
        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                try {
                    JSONObject payload = StreamUtility.downloadUriAsJSONObject(url);
                    
                    
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            // TODO: Localize...
                            showAlertDialog(context, "There was an error processing your request. Please try again later!");
                        }
                    });
                }
            }
        });
    }
    
}
