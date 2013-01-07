package com.clockworkmod.billing;

import org.json.JSONObject;

public interface Order {
    JSONObject getRawJSONObject();
    String getDeveloperPayload();
    String getProductId();
    long getPurchaseTime();
    long getTimestamp();
}
