package com.clockworkmod.billing;

import org.json.JSONObject;

public class ClockworkOrder implements Order {
    public JSONObject getRawJSONObject() {
        return mOrder;
    }
    
    JSONObject mOrder;
    ClockworkOrder(JSONObject order, long timestamp) {
        mOrder = order;
        this.timestamp = timestamp;
    }

    @Override
    public String getDeveloperPayload() {
        return mOrder.optString("custom_payload", null);
    }
    
    public String getProductId() {
        return mOrder.optString("product_id", null);
    }
    
    public long getPurchaseTime() {
        return mOrder.optLong("order_date", 0);
    }

    private long timestamp;
    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
