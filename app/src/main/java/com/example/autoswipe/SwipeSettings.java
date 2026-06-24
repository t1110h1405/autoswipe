package com.example.autoswipe;

final class SwipeSettings {
    static final String PREFS = "auto_swipe_settings";
    static final String ACTION_START = "com.example.autoswipe.START";
    static final String ACTION_STOP = "com.example.autoswipe.STOP";
    static final String ACTION_REFRESH_OVERLAY = "com.example.autoswipe.REFRESH_OVERLAY";
    static final String KEY_RUNNING = "running";
    static final String KEY_UNLOCKED = "unlocked";
    static final String KEY_MODE = "mode";
    static final String KEY_DIRECTION = "direction";
    static final String KEY_INTERVAL_MS = "interval_ms";
    static final String KEY_DURATION_MS = "duration_ms";
    static final String KEY_DISTANCE_PERCENT = "distance_percent";
    static final String KEY_TARGET_X_PERCENT = "target_x_percent";
    static final String KEY_TARGET_Y_PERCENT = "target_y_percent";
    static final String KEY_TIMER_MINUTES = "timer_minutes";
    static final String KEY_LOCK_ON_TIMER = "lock_on_timer";

    static final String MODE_SWIPE = "swipe";
    static final String MODE_TAP = "tap";

    static final String DIRECTION_UP = "up";
    static final String DIRECTION_DOWN = "down";
    static final String DIRECTION_LEFT = "left";
    static final String DIRECTION_RIGHT = "right";

    static final int DEFAULT_INTERVAL_MS = 3000;
    static final int DEFAULT_DURATION_MS = 450;
    static final int DEFAULT_DISTANCE_PERCENT = 55;
    static final int DEFAULT_TARGET_X_PERCENT = 50;
    static final int DEFAULT_TARGET_Y_PERCENT = 50;
    static final int DEFAULT_TIMER_MINUTES = 0;

    private SwipeSettings() {
    }
}
