package com.clockworkmod.billing;

public class CheckPurchaseResult {
    static final int _PURCHASED = 0;
    static final int _NOT_PURCHASED = 1;
    static final int _ERROR = 2;
    static final int _PENDING = 3;
    
    static final int _STALE = 4;
    
    CheckPurchaseResult(int state) {
        mState = state;
    }
    
    int mState;
    public boolean isPurchased() {
        return mState == _PURCHASED;
    }
    
    public boolean isNotPurchased() {
        return mState == _NOT_PURCHASED;
    }
    
    public boolean isError() {
        return mState == _ERROR;
    }
    
    public boolean isPending() {
        return mState == _PENDING;
    }    

    public boolean isStale() {
        return mState == _STALE;
    }
    
    Order mOrder;
    public Order getOrder() {
        return mOrder;
    }
    
    static CheckPurchaseResult purchased(Order order) { 
        CheckPurchaseResult ret = new CheckPurchaseResult(_PURCHASED);
        ret.mOrder = order;
        return ret;
    }

    static CheckPurchaseResult notPurchased() {
        return new CheckPurchaseResult(_NOT_PURCHASED);
    }
    static CheckPurchaseResult error() { 
        return new CheckPurchaseResult(_ERROR);
    }
    static CheckPurchaseResult pending() { 
        return new CheckPurchaseResult(_PENDING);
    }
    static CheckPurchaseResult stale() { 
        return new CheckPurchaseResult(_STALE);
    }
}
