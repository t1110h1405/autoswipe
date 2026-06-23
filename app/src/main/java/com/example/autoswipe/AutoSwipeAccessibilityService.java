package com.example.autoswipe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AutoSwipeAccessibilityService extends AccessibilityService {
    private static final long START_DELAY_MS = 3000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private WindowManager windowManager;
    private View overlayView;
    private View targetPickerView;
    private TextView overlayStatusView;
    private Button overlayToggleButton;
    private Button overlayModeButton;
    private Button overlayTargetButton;
    private boolean receiverRegistered;

    private final Runnable actionLoop = new Runnable() {
        @Override
        public void run() {
            if (!prefs.getBoolean(SwipeSettings.KEY_RUNNING, false)) {
                return;
            }

            performSelectedAction();
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
            } else if (SwipeSettings.ACTION_REFRESH_OVERLAY.equals(intent.getAction())) {
                showOverlayIfAllowed();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences(SwipeSettings.PREFS, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerControlReceiver();
        showOverlayIfAllowed();
        if (prefs.getBoolean(SwipeSettings.KEY_RUNNING, false)) {
            startSwiping();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        stopActions();
        removeOverlay();
        removeTargetPicker();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopActions();
        removeOverlay();
        removeTargetPicker();
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
        filter.addAction(SwipeSettings.ACTION_REFRESH_OVERLAY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void startSwiping() {
        if (!prefs.getBoolean(SwipeSettings.KEY_UNLOCKED, false)) {
            return;
        }
        handler.removeCallbacks(actionLoop);
        handler.postDelayed(actionLoop, START_DELAY_MS);
        updateOverlayState();
    }

    private void stopSwiping() {
        stopActions();
    }

    private void stopActions() {
        handler.removeCallbacks(actionLoop);
        updateOverlayState();
    }

    private void showOverlayIfAllowed() {
        if (!prefs.getBoolean(SwipeSettings.KEY_UNLOCKED, false)) {
            removeOverlay();
            return;
        }
        if (windowManager == null || overlayView != null) {
            updateOverlayState();
            return;
        }

        overlayView = buildOverlayView();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(16);
        params.y = dp(140);
        attachDragHandler(overlayView, params);
        windowManager.addView(overlayView, params);
        updateOverlayState();
    }

    private View buildOverlayView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(230, 17, 24, 39));
        background.setCornerRadius(dp(10));
        root.setBackground(background);

        TextView title = new TextView(this);
        title.setText("Auto Swipe");
        title.setTextColor(Color.WHITE);
        title.setTextSize(12);
        root.addView(title);

        overlayStatusView = new TextView(this);
        overlayStatusView.setTextColor(Color.WHITE);
        overlayStatusView.setTextSize(12);
        root.addView(overlayStatusView);

        overlayModeButton = new Button(this);
        overlayModeButton.setAllCaps(false);
        overlayModeButton.setMinWidth(dp(112));
        overlayModeButton.setOnClickListener(v -> toggleMode());
        root.addView(overlayModeButton);

        overlayTargetButton = new Button(this);
        overlayTargetButton.setText("位置選択");
        overlayTargetButton.setAllCaps(false);
        overlayTargetButton.setMinWidth(dp(112));
        overlayTargetButton.setOnClickListener(v -> showTargetPicker());
        root.addView(overlayTargetButton);

        overlayToggleButton = new Button(this);
        overlayToggleButton.setAllCaps(false);
        overlayToggleButton.setMinWidth(dp(112));
        overlayToggleButton.setOnClickListener(v -> toggleFromOverlay());
        root.addView(overlayToggleButton);

        return root;
    }

    private void attachDragHandler(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean moved;

            @Override
            public boolean onTouch(View touchedView, MotionEvent event) {
                if (windowManager == null) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - initialTouchX);
                        int dy = Math.round(event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                            moved = true;
                        }
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(touchedView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            touchedView.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void toggleFromOverlay() {
        boolean running = prefs.getBoolean(SwipeSettings.KEY_RUNNING, false);
        prefs.edit().putBoolean(SwipeSettings.KEY_RUNNING, !running).apply();
        if (running) {
            stopActions();
        } else {
            startSwiping();
        }
    }

    private void updateOverlayState() {
        if (overlayStatusView == null || overlayToggleButton == null || overlayModeButton == null) {
            return;
        }
        boolean running = prefs.getBoolean(SwipeSettings.KEY_RUNNING, false);
        String mode = prefs.getString(SwipeSettings.KEY_MODE, SwipeSettings.MODE_SWIPE);
        int xPercent = prefs.getInt(SwipeSettings.KEY_TARGET_X_PERCENT, SwipeSettings.DEFAULT_TARGET_X_PERCENT);
        int yPercent = prefs.getInt(SwipeSettings.KEY_TARGET_Y_PERCENT, SwipeSettings.DEFAULT_TARGET_Y_PERCENT);
        overlayStatusView.setText((running ? "実行中" : "停止中") + " / 位置 " + xPercent + "%, " + yPercent + "%");
        overlayModeButton.setText(SwipeSettings.MODE_TAP.equals(mode) ? "モード: タップ" : "モード: スワイプ");
        overlayToggleButton.setText(running ? "停止" : "開始");
    }

    private void toggleMode() {
        String mode = prefs.getString(SwipeSettings.KEY_MODE, SwipeSettings.MODE_SWIPE);
        String nextMode = SwipeSettings.MODE_TAP.equals(mode) ? SwipeSettings.MODE_SWIPE : SwipeSettings.MODE_TAP;
        prefs.edit().putString(SwipeSettings.KEY_MODE, nextMode).apply();
        updateOverlayState();
    }

    private void showTargetPicker() {
        if (windowManager == null || targetPickerView != null) {
            return;
        }
        boolean wasRunning = prefs.getBoolean(SwipeSettings.KEY_RUNNING, false);
        if (wasRunning) {
            prefs.edit().putBoolean(SwipeSettings.KEY_RUNNING, false).apply();
            stopActions();
        }

        FrameLayout picker = new FrameLayout(this);
        picker.setBackgroundColor(Color.argb(95, 0, 0, 0));
        TextView guide = new TextView(this);
        guide.setText("実行したい位置をタップ");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(18);
        guide.setGravity(Gravity.CENTER);
        guide.setBackgroundColor(Color.argb(180, 17, 24, 39));
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(72),
                Gravity.TOP
        );
        picker.addView(guide, guideParams);
        picker.setOnTouchListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return true;
            }
            saveTargetPosition(event.getRawX(), event.getRawY());
            removeTargetPicker();
            if (wasRunning) {
                prefs.edit().putBoolean(SwipeSettings.KEY_RUNNING, true).apply();
                startSwiping();
            }
            return true;
        });

        targetPickerView = picker;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(targetPickerView, params);
    }

    private void saveTargetPosition(float rawX, float rawY) {
        DisplayMetrics metrics = getDisplayMetrics();
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            return;
        }
        int xPercent = clamp(Math.round(rawX * 100f / metrics.widthPixels), 1, 99);
        int yPercent = clamp(Math.round(rawY * 100f / metrics.heightPixels), 1, 99);
        prefs.edit()
                .putInt(SwipeSettings.KEY_TARGET_X_PERCENT, xPercent)
                .putInt(SwipeSettings.KEY_TARGET_Y_PERCENT, yPercent)
                .apply();
        updateOverlayState();
    }

    private void removeTargetPicker() {
        if (windowManager != null && targetPickerView != null) {
            windowManager.removeView(targetPickerView);
        }
        targetPickerView = null;
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
        }
        overlayView = null;
        overlayStatusView = null;
        overlayToggleButton = null;
        overlayModeButton = null;
        overlayTargetButton = null;
    }

    private void performSelectedAction() {
        String mode = prefs.getString(SwipeSettings.KEY_MODE, SwipeSettings.MODE_SWIPE);
        if (SwipeSettings.MODE_TAP.equals(mode)) {
            performTap();
        } else {
            performSwipe();
        }
    }

    private void performTap() {
        DisplayMetrics metrics = getDisplayMetrics();
        float x = getTargetX(metrics);
        float y = getTargetY(metrics);

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void performSwipe() {
        DisplayMetrics metrics = getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        float centerX = getTargetX(metrics);
        float centerY = getTargetY(metrics);
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

        startX = clamp(startX, 1f, width - 1f);
        startY = clamp(startY, 1f, height - 1f);
        endX = clamp(endX, 1f, width - 1f);
        endY = clamp(endY, 1f, height - 1f);

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        int durationMs = prefs.getInt(SwipeSettings.KEY_DURATION_MS, SwipeSettings.DEFAULT_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(durationMs, 100)))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager currentWindowManager = windowManager != null
                ? windowManager
                : (WindowManager) getSystemService(WINDOW_SERVICE);
        if (currentWindowManager != null) {
            currentWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        return metrics;
    }

    private float getTargetX(DisplayMetrics metrics) {
        return metrics.widthPixels
                * prefs.getInt(SwipeSettings.KEY_TARGET_X_PERCENT, SwipeSettings.DEFAULT_TARGET_X_PERCENT)
                / 100f;
    }

    private float getTargetY(DisplayMetrics metrics) {
        return metrics.heightPixels
                * prefs.getInt(SwipeSettings.KEY_TARGET_Y_PERCENT, SwipeSettings.DEFAULT_TARGET_Y_PERCENT)
                / 100f;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
