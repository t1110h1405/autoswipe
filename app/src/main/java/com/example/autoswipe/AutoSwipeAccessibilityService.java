package com.example.autoswipe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class AutoSwipeAccessibilityService extends AccessibilityService {
    private static final long START_DELAY_MS = 3000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean receiverRegistered;

    private final Runnable swipeLoop = new Runnable() {
        @Override
        public void run() {
            if (!prefs.getBoolean(SwipeSettings.KEY_RUNNING, false)) {
                return;
            }

            performSwipe();
            int intervalMs = prefs.getInt(SwipeSettings.KEY_INTERVAL_MS, SwipeSettings.DEFAULT_INTERVAL_MS);
            handler.postDelayed(this, Math.max(intervalMs, 500));
        }
    };

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (SwipeSettings.ACTION_START.equals(intent.getAction())) {
                startSwiping();
            } else if (SwipeSettings.ACTION_STOP.equals(intent.getAction())) {
                stopSwiping();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences(SwipeSettings.PREFS, MODE_PRIVATE);
        registerControlReceiver();
        if (prefs.getBoolean(SwipeSettings.KEY_RUNNING, false)) {
            startSwiping();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        stopSwiping();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopSwiping();
        if (receiverRegistered) {
            unregisterReceiver(controlReceiver);
            receiverRegistered = false;
        }
        prefs.edit().putBoolean(SwipeSettings.KEY_RUNNING, false).apply();
        return super.onUnbind(intent);
    }

    private void registerControlReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SwipeSettings.ACTION_START);
        filter.addAction(SwipeSettings.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void startSwiping() {
        handler.removeCallbacks(swipeLoop);
        handler.postDelayed(swipeLoop, START_DELAY_MS);
    }

    private void stopSwiping() {
        handler.removeCallbacks(swipeLoop);
    }

    private void performSwipe() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        float distance = Math.min(width, height)
                * prefs.getInt(SwipeSettings.KEY_DISTANCE_PERCENT, SwipeSettings.DEFAULT_DISTANCE_PERCENT)
                / 100f;

        String direction = prefs.getString(SwipeSettings.KEY_DIRECTION, SwipeSettings.DIRECTION_UP);
        float startX = centerX;
        float startY = centerY;
        float endX = centerX;
        float endY = centerY;

        if (SwipeSettings.DIRECTION_UP.equals(direction)) {
            startY = centerY + distance / 2f;
            endY = centerY - distance / 2f;
        } else if (SwipeSettings.DIRECTION_DOWN.equals(direction)) {
            startY = centerY - distance / 2f;
            endY = centerY + distance / 2f;
        } else if (SwipeSettings.DIRECTION_LEFT.equals(direction)) {
            startX = centerX + distance / 2f;
            endX = centerX - distance / 2f;
        } else if (SwipeSettings.DIRECTION_RIGHT.equals(direction)) {
            startX = centerX - distance / 2f;
            endX = centerX + distance / 2f;
        }

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        int durationMs = prefs.getInt(SwipeSettings.KEY_DURATION_MS, SwipeSettings.DEFAULT_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(durationMs, 100)))
                .build();
        dispatchGesture(gesture, null, null);
    }
}
