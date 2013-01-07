package com.clockworkmod.billing;

import org.json.JSONObject;

public class InAppOrder implements Order {
    public JSONObject getRawJSONObject() {
        return mOrder;
    }
    
    JSONObject mOrder;
    InAppOrder(JSONObject order, long timestamp) {
        mOrder = order;
        this.timestamp = timestamp;
    }

    @Override
    public String getDeveloperPayload() {
        return mOrder.optString("developerPayload", null);
    }
    
    public String getProductId() {
        return mOrder.optString("productId", null);
    }
    
    public long getPurchaseTime() {
        return mOrder.optLong("purchaseTime", 0);
    }

    private long timestamp;
    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
