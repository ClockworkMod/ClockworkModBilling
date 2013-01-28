package com.clockworkmod.billing;

import android.os.Handler;

public abstract class ThreadingRunnable implements Runnable {
    Thread thread;
    
    public Thread getThread() {
        return thread;
    }

    public static ThreadingRunnable background(final ThreadingRunnable runnable) {
        runnable.thread = new Thread() {
            public void run() {
                runnable.run();
            };
        };
        
        runnable.thread.start();
        return runnable;
    }
    
    public ThreadingRunnable() {
        mHandler = new Handler();
    }

    public ThreadingRunnable(Handler handler) {
        mHandler = handler;
    }

    public abstract void run();
    private Handler mHandler;
    public Handler getHandler() {
        return mHandler;
    }
    
    public ThreadingRunnable foreground(Runnable runnable) {
        mHandler.post(runnable);
        return this;
    }
    
    public ThreadingRunnable background(Runnable runnable) {
        new Thread(runnable).start();
        return this;
    }
}
