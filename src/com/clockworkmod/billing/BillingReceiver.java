/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clockworkmod.billing;

import java.util.UUID;

import org.apache.http.client.ResponseHandler;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.android.vending.billing.Consts;
import com.android.vending.billing.Consts.ResponseCode;
import com.android.vending.billing.IMarketBillingService;

/**
 * This class implements the broadcast receiver for in-app billing. All asynchronous messages from
 * Android Market come to this app through this receiver. This class forwards all
 * messages to the {@link BillingService}, which can start background threads,
 * if necessary, to process the messages. This class runs on the UI thread and must not do any
 * network I/O, database updates, or any tasks that might take a long time to complete.
 * It also must not start a background thread because that may be killed as soon as
 * {@link #onReceive(Context, Intent)} returns.
 *
 * You should modify and obfuscate this code before using it.
 */
public class BillingReceiver extends BroadcastReceiver {
    private static final String TAG = "BillingReceiver";
    public static final String PURCHASE_STATE_CHANGED = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".PURCHASE_STATE_CHANGED";
    public static final String IN_APP_NOTIFY = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".IN_APP_NOTIFY";

    public static final String SUCCEEDED = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".SUCCEEDED";
    public static final String FAILED = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".FAILED";
    public static final String CANCELLED = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".CANCELLED";
    
    static Bundle makeRequestBundle(Context context, String method) {
        Bundle request = new Bundle();
        request.putString(Consts.BILLING_REQUEST_METHOD, method);
        request.putInt(Consts.BILLING_REQUEST_API_VERSION, 1);
        request.putString(Consts.BILLING_REQUEST_PACKAGE_NAME, context.getPackageName());
        return request;
    }

    /**
     * This is the entry point for all asynchronous messages sent from Android Market to
     * the application. This method forwards the messages on to the
     * {@link BillingService}, which handles the communication back to Android Market.
     * The {@link BillingService} also reports state changes back to the application through
     * the {@link ResponseHandler}.
     */
    @Override
    public void onReceive(final Context context, Intent intent) {
        intent.setClass(context, BillingService.class);
        context.startService(intent);
    }
}
