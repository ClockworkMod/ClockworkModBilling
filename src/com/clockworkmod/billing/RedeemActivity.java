package com.clockworkmod.billing;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.widget.EditText;

import org.json.JSONObject;

public class RedeemActivity extends Activity {
    static final String AUTH_TOKEN_TYPE = "ah";
    String accountName;
    String productId;
    String buyerId;

    private static final int REQUEST_AUTH = 500001;

    public static final int BILLING_RESULT_PURCHASED = 10000;
    public static final int BILLING_RESULT_NOT_PURCHASED = 10001;
    public static final int BILLING_RESULT_CANCELED = 10002;
    public static final int BILLING_RESULT_FAILURE = 10003;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_AUTH) {
            if (resultCode == Activity.RESULT_OK) {
                tryAuth();
            }
            else {
                setResult(BILLING_RESULT_CANCELED);
                finish();
            }
        }
    }

    Handler handler = new Handler();
    private void tryAuth() {
        AccountManager accountManager = AccountManager.get(this);
        if (accountManager == null)
            return;
        Account account = new Account(accountName, "com.google");
        final SharedPreferences settings = ClockworkModBillingClient.getInstance().getCachedSettings();
        String curAuthToken = settings.getString("authToken", null);
        final Editor editor = settings.edit();
        if (curAuthToken != null) {
            accountManager.invalidateAuthToken(account.type, curAuthToken);
            editor.remove("authToken");
            editor.commit();
        }
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Please wait...");
        dialog.show();
        accountManager.getAuthToken(account, AUTH_TOKEN_TYPE, null, this, new AccountManagerCallback<Bundle>() {
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Bundle bundle = future.getResult();
                    final String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    if (authToken == null) {
                        Intent authIntent = (Intent) bundle.get(AccountManager.KEY_INTENT);
                        // for some reason it sets the intent flag with FLAG_ACTIVITY_NEW_TASK
                        // This ends up sending an immediatae Activity.RESULT_CANCELLED event to
                        // the calling activity.
                        authIntent.setFlags(0);
                        startActivityForResult(authIntent, REQUEST_AUTH);
                    } else {
                        editor.putString("authToken", authToken);
                        editor.putString("buyerEmail", accountName.toLowerCase());
                        editor.commit();

                        ClockworkModBillingClient.getInstance().checkPurchase(RedeemActivity.this, productId, buyerId, ClockworkModBillingClient.CACHE_DURATION_FOREVER, 0, new CheckPurchaseCallback() {
                            @Override
                            public void onFinished(CheckPurchaseResult result) {
                                if (result.isPurchased()) {
                                    setResult(BILLING_RESULT_PURCHASED);
                                    dialog.dismiss();
                                    success();
                                    return;
                                }

                                ClockworkModBillingClient.getInstance().startPurchase(RedeemActivity.this,
                                productId, LinkPurchase.REQUIRE_EMAIL, true,
                                ClockworkModBillingClient.getInstance().getSafeDeviceId(RedeemActivity.this),
                                PurchaseType.REDEEM, new PurchaseCallback() {
                                    @Override
                                    public void onFinished(PurchaseResult result) {
                                        dialog.dismiss();
                                        if (result == PurchaseResult.SUCCEEDED)
                                            success();
                                        else
                                            finish();
                                    }
                                }
                                );
                            }
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    setResult(BILLING_RESULT_FAILURE);
                    finish();
                }
            }
        }, handler);
    }

    void success() {
        new AlertDialog.Builder(this)
        .setTitle("Code Redeemed")
        .setMessage("Your code was redeemed. Thanks!")
        .setCancelable(false)
        .setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        })
        .create().show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        setResult(BILLING_RESULT_CANCELED);
        productId = intent.getStringExtra("productId");
        buyerId = intent.getStringExtra("buyerId");

        // let's prompt for a buyer email
        AccountManager amgr = AccountManager.get(this);
        final Account[] accounts = amgr.getAccountsByType("com.google");

        String[] accountOptions = new String[accounts.length];

        for (int i = 0; i < accounts.length; i++) {
            Account account = accounts[i];
            accountOptions[i] = account.name;
        }

        Builder builder = new Builder(this);
        builder.setItems(accountOptions, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                accountName = accounts[which].name;
                tryAuth();
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                setResult(BILLING_RESULT_CANCELED);
                finish();
            }
        });

        builder.setTitle("Redeem to Account");
        builder.create().show();
    }
}
