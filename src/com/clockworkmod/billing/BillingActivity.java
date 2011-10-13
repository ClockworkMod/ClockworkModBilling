package com.clockworkmod.billing;

import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.widget.EditText;

public class BillingActivity extends Activity {
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

    private void tryAuth() {
        AccountManager accountManager = AccountManager.get(this);
        Account account = new Account(accountName, "com.google");
        final SharedPreferences settings = ClockworkModBillingClient.getInstance().getCachedSettings();
        String curAuthToken = settings.getString("authToken", null);
        final Editor editor = settings.edit();
        if (curAuthToken != null) {
            accountManager.invalidateAuthToken(account.type, curAuthToken);
            editor.remove("authToken");
            editor.commit();
        }
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Please wait...");
        dialog.show();
        accountManager.getAuthToken(account, AUTH_TOKEN_TYPE, false, new AccountManagerCallback<Bundle>() {
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
                    }
                    else {
                        editor.putString("authToken", authToken);
                        editor.putString("buyerEmail", accountName.toLowerCase());
                        editor.commit();
                        
                        ClockworkModBillingClient.getInstance().checkPurchase(BillingActivity.this, productId, buyerId, ClockworkModBillingClient.CACHE_DURATION_FOREVER, 0, new CheckPurchaseCallback() {
                            @Override
                            public void onFinished(CheckPurchaseResult result) {
                                if (result == CheckPurchaseResult.PURCHASED) {
                                    setResult(BILLING_RESULT_PURCHASED);
                                }
                                else if (result == CheckPurchaseResult.NOT_PURCHASED) {
                                    checkEmail(accountName.toLowerCase());
                                    //checkEmail("buyer_1279241028_per@hotmail.com");
                                    return;
                                }
                                else {
                                    setResult(BILLING_RESULT_FAILURE);
                                }
                                finish();
                            }
                        });
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    setResult(BILLING_RESULT_FAILURE);
                    finish();
                }
            }
        }, null);
    }
    
    private void checkEmail(final String payerEmail) {
        final ProgressDialog progress = new ProgressDialog(BillingActivity.this);
        progress.setMessage("Checking for purchase...");
        progress.show();
        // now attempt to recover the purchase
        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                try {
                    String url = String.format(ClockworkModBillingClient.TRANSFER_URL, ClockworkModBillingClient.getInstance().mSellerId, productId, payerEmail, buyerId, ClockworkModBillingClient.getInstance().mSandbox);
                    final JSONObject result = StreamUtility.downloadUriAsJSONObject(url);
                    String _message = result.getString("result");
                    final boolean success = result.optBoolean("success", false);
                    final boolean wait = result.optBoolean("wait", false);
                    if (wait)
                        _message += "\n\nPress Ok after you have read the email sent to your account.";
                    final String message = _message;
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            progress.cancel();
                            AlertDialog.Builder builder = new Builder(BillingActivity.this);
                            builder.setMessage(message);
                            builder.setOnCancelListener(new OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    setResult(BILLING_RESULT_CANCELED);
                                    finish();
                                }
                            });
                            builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (success) {
                                        setResult(BILLING_RESULT_PURCHASED);
                                        finish();
                                        return;
                                    }

                                    if (!wait) {
                                        setResult(BILLING_RESULT_CANCELED);
                                        finish();
                                        return;
                                    }

                                    progress.show();
                                    ClockworkModBillingClient.getInstance().checkPurchase(BillingActivity.this, productId, buyerId, ClockworkModBillingClient.CACHE_DURATION_FOREVER, 0, new CheckPurchaseCallback() {
                                        @Override
                                        public void onFinished(CheckPurchaseResult result) {
                                            progress.cancel();
                                            if (result == CheckPurchaseResult.PURCHASED) {
                                                setResult(BILLING_RESULT_PURCHASED);
                                            }
                                            else if (result == CheckPurchaseResult.NOT_PURCHASED) {
                                                setResult(BILLING_RESULT_NOT_PURCHASED);
                                            }
                                            else {
                                                setResult(BILLING_RESULT_FAILURE);
                                            }
                                            finish();
                                        }
                                    });
                                }
                            });
                            
                            final AlertDialog d = builder.create();
                            d.show();
                            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                            getHandler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                }
                            }, 7000);
                        }
                    });
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            progress.cancel();
                            setResult(BILLING_RESULT_FAILURE);
                            finish();
                        }
                    });
                }
            }
        });
    }

    private void promptEmail() {
        AlertDialog.Builder enterEmail = new Builder(BillingActivity.this);
        enterEmail.setTitle("PayPal Email");
        final EditText ed = new EditText(BillingActivity.this);
        if (ClockworkModBillingClient.getInstance().mSandbox)
            ed.setText("buyer_1279241028_per@hotmail.com");
        enterEmail.setView(ed);
        enterEmail.setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String payerEmail = ed.getText().toString();
                checkEmail(payerEmail);
            }
        });
        
        enterEmail.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                setResult(BILLING_RESULT_CANCELED);
                finish();
            }
        });
        
        enterEmail.create().show();
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if ("recover".equals(intent.getStringExtra("action"))) {
            setResult(BILLING_RESULT_CANCELED);
            productId = intent.getStringExtra("productId");
            buyerId = intent.getStringExtra("buyerId");
            
            // let's prompt for a buyer email
            AccountManager amgr = AccountManager.get(this);
            final Account[] accounts = amgr.getAccountsByType("com.google");

            String[] accountOptions = new String[accounts.length + 1];
            accountOptions[accounts.length] = "Enter PayPal Email";

            for (int i = 0; i < accounts.length; i++) {
                Account account = accounts[i];
                accountOptions[i] = account.name;
            }

            AlertDialog.Builder builder = new Builder(this);
            builder.setItems(accountOptions, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == accounts.length) {
                        promptEmail();
                        return;
                    }
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
            
            builder.setTitle("Recover Account Purchases");
            builder.create().show();
        }
    }
}
