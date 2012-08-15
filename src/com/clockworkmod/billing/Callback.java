package com.clockworkmod.billing;

public interface Callback<T> {
    public void onFinished(T response);
}
