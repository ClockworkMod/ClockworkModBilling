package com.clockworkmod.billing;

public interface UpdateTrialCallback {
    void onFinished(boolean success, long trialStartDate, long trialIncrement, long trialDailyIncrement, long trialDailyWindow);
}
