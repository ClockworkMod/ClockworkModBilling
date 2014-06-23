package com.clockworkmod.billing;

import org.json.JSONObject;

/**
 * Created by koush on 6/23/14.
 */
public class AmazonOrder implements Order {
    @Override
    public JSONObject getRawJSONObject() {
        return null;
    }

    @Override
    public String getDeveloperPayload() {
        return null;
    }

    @Override
    public String getProductId() {
        return null;
    }

    @Override
    public long getPurchaseTime() {
        return 0;
    }

    @Override
    public long getTimestamp() {
        return 0;
    }
}
