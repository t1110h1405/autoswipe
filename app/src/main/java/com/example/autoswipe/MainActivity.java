package com.example.autoswipe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class MainActivity extends android.app.Activity {
    private static final String PASSCODE_HASH = "fb4a364525afe39bad19445304e509c0d3b7f82d2f22aa85e8f22979b291e6a0";

    private SharedPreferences prefs;
    private TextView statusView;
    private Button startStopButton;
    private Spinner directionSpinner;
    private Spinner intervalSpinner;
    private Spinner durationSpinner;
    private Spinner distanceSpinner;

    private final String[] directionLabels = {"上へ", "下へ", "左へ", "右へ"};
    private final String[] directionValues = {
            SwipeSettings.DIRECTION_UP,
            SwipeSettings.DIRECTION_DOWN,
            SwipeSettings.DIRECTION_LEFT,
            SwipeSettings.DIRECTION_RIGHT
    };
    private final String[] intervalLabels = {"1秒", "2秒", "3秒", "5秒", "10秒"};
    private final int[] intervalValues = {1000, 2000, 3000, 5000, 10000};
    private final String[] durationLabels = {"短い", "標準", "ゆっくり"};
    private final int[] durationValues = {250, 450, 800};
    private final String[] distanceLabels = {"短め", "標準", "長め", "最大"};
    private final int[] distanceValues = {35, 55, 75, 90};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(SwipeSettings.PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(SwipeSettings.KEY_UNLOCKED, false)) {
            showMainScreen();
        } else {
            setContentView(buildUnlockView());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.getBoolean(SwipeSettings.KEY_UNLOCKED, false) && statusView != null) {
            refreshStatus();
        }
    }

    private View buildUnlockView() {
        int padding = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Auto Swipe");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView prompt = new TextView(this);
        prompt.setText("パスコードを入力してください");
        prompt.setTextSize(15);
        prompt.setPadding(0, dp(16), 0, dp(8));
        root.addView(prompt, matchWrap());

        EditText passcodeInput = new EditText(this);
        passcodeInput.setSingleLine(true);
        passcodeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passcodeInput, matchWrap());

        TextView errorView = new TextView(this);
        errorView.setTextSize(13);
        errorView.setPadding(0, dp(8), 0, dp(8));
        root.addView(errorView, matchWrap());

        Button unlockButton = new Button(this);
        unlockButton.setText("解除");
        unlockButton.setAllCaps(false);
        unlockButton.setOnClickListener(v -> {
            String passcode = passcodeInput.getText().toString();
            if (PASSCODE_HASH.equals(sha256(passcode))) {
                prefs.edit().putBoolean(SwipeSettings.KEY_UNLOCKED, true).apply();
                showMainScreen();
            } else {
                errorView.setText("パスコードが違います");
            }
        });
        root.addView(unlockButton, matchWrap());

        return root;
    }

    private void showMainScreen() {
        setContentView(buildContentView());
        bindSettings();
        refreshStatus();
        sendBroadcast(new Intent(SwipeSettings.ACTION_REFRESH_OVERLAY).setPackage(getPackageName()));
    }

    private View buildContentView() {
        int padding = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Auto Swipe");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView, matchWrap());

        directionSpinner = addSpinner(root, "方向", directionLabels);
        intervalSpinner = addSpinner(root, "間隔", intervalLabels);
        durationSpinner = addSpinner(root, "スワイプ速度", durationLabels);
        distanceSpinner = addSpinner(root, "スワイプ距離", distanceLabels);

        startStopButton = new Button(this);
        startStopButton.setAllCaps(false);
        startStopButton.setOnClickListener(v -> toggleRunning());
        root.addView(startStopButton, matchWrap());

        Button settingsButton = new Button(this);
        settingsButton.setText("ユーザー補助設定を開く");
        settingsButton.setAllCaps(false);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settingsButton, matchWrap());

        Button autoLockSettingsButton = new Button(this);
        autoLockSettingsButton.setText("画面消灯・自動ロック設定を開く");
        autoLockSettingsButton.setAllCaps(false);
        autoLockSettingsButton.setOnClickListener(v -> openAutoLockSettings());
        root.addView(autoLockSettingsButton, matchWrap());

        TextView note = new TextView(this);
        note.setText("開始後、対象アプリに切り替えてください。停止するときはこのアプリに戻って停止します。");
        note.setTextSize(13);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note, matchWrap());

        return root;
    }

    private void openAutoLockSettings() {
        Intent displaySettings = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (displaySettings.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(displaySettings);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Fall back to the main settings screen on manufacturer-specific devices.
            }
        }
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private Spinner addSpinner(LinearLayout root, String labelText, String[] labels) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(14);
        label.setPadding(0, dp(12), 0, dp(4));
        root.addView(label, matchWrap());

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        root.addView(spinner, matchWrap());
        return spinner;
    }

    private void bindSettings() {
        setSpinnerSelection(directionSpinner, directionValues, prefs.getString(SwipeSettings.KEY_DIRECTION, SwipeSettings.DIRECTION_UP));
        setSpinnerSelection(intervalSpinner, intervalValues, prefs.getInt(SwipeSettings.KEY_INTERVAL_MS, SwipeSettings.DEFAULT_INTERVAL_MS));
        setSpinnerSelection(durationSpinner, durationValues, prefs.getInt(SwipeSettings.KEY_DURATION_MS, SwipeSettings.DEFAULT_DURATION_MS));
        setSpinnerSelection(distanceSpinner, distanceValues, prefs.getInt(SwipeSettings.KEY_DISTANCE_PERCENT, SwipeSettings.DEFAULT_DISTANCE_PERCENT));

        directionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putString(SwipeSettings.KEY_DIRECTION, directionValues[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(SwipeSettings.KEY_INTERVAL_MS, intervalValues[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        durationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(SwipeSettings.KEY_DURATION_MS, durationValues[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        distanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(SwipeSettings.KEY_DISTANCE_PERCENT, distanceValues[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void toggleRunning() {
        if (!isAccessibilityServiceEnabled()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        boolean running = prefs.getBoolean(SwipeSettings.KEY_RUNNING, false);
        if (running) {
            prefs.edit().putBoolean(SwipeSettings.KEY_RUNNING, false).apply();
            sendBroadcast(new Intent(SwipeSettings.ACTION_STOP).setPackage(getPackageName()));
        } else {
            prefs.edit().putBoolean(SwipeSettings.KEY_RUNNING, true).apply();
            sendBroadcast(new Intent(SwipeSettings.ACTION_START).setPackage(getPackageName()));
        }
        refreshStatus();
    }

    private void refreshStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        boolean running = prefs.getBoolean(SwipeSettings.KEY_RUNNING, false);

        if (!enabled) {
            statusView.setText("状態: ユーザー補助サービスがOFFです");
            startStopButton.setText("設定を開いて有効化");
            return;
        }

        statusView.setText(running ? "状態: 実行中" : "状態: 停止中");
        startStopButton.setText(running ? "停止" : "開始");
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String expected = getPackageName() + "/" + AutoSwipeAccessibilityService.class.getName();
        for (AccessibilityServiceInfo service : services) {
            String id = service.getId();
            if (!TextUtils.isEmpty(id) && id.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private void setSpinnerSelection(Spinner spinner, String[] values, String selected) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selected)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void setSpinnerSelection(Spinner spinner, int[] values, int selected) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == selected) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
