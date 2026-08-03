package com.example.autoswipe;

import android.app.Application;

public class AutoSwipeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SharedUrlManager.start(this);
    }
}
