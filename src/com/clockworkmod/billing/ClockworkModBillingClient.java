package com.clockworkmod.billing;

import android.content.Context;
import android.os.Handler;

public class ClockworkModBillingClient {
    private static ClockworkModBillingClient mInstance;
    Context mContext;
    
    private ClockworkModBillingClient(Context context) {
        mContext = context.getApplicationContext();
    }
    
    public static ClockworkModBillingClient getInstance(Context context) {
        if (mInstance != null)
            return mInstance;
        return mInstance = new ClockworkModBillingClient(context);
    }
    
    public void startPurchase(final String productId) {
        final Handler handler = new Handler();
    }
    
}
