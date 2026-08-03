package com.example.autoswipe;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

final class SharedUrlManager {
    private static final int MAX_URL_LENGTH = 2048;
    private static boolean started;

    private SharedUrlManager() {
    }

    static synchronized void start(Context context) {
        if (started) {
            return;
        }
        started = true;
        Context applicationContext = context.getApplicationContext();
        SharedPreferences prefs = applicationContext.getSharedPreferences(
                SwipeSettings.PREFS,
                Context.MODE_PRIVATE
        );
        try {
            DatabaseReference sharedUrlReference = FirebaseDatabase.getInstance()
                    .getReference("sharedUrl");
            sharedUrlReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    String url = snapshot.child("url").getValue(String.class);
                    Long updatedAt = snapshot.child("updatedAt").getValue(Long.class);
                    String safeUrl = isValidHttpUrl(url) ? url.trim() : "";
                    prefs.edit()
                            .putString(SwipeSettings.KEY_SHARED_URL, safeUrl)
                            .putLong(
                                    SwipeSettings.KEY_SHARED_URL_UPDATED_AT,
                                    updatedAt == null ? 0L : updatedAt
                            )
                            .apply();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    // Keep the last valid cached URL when the network is unavailable.
                }
            });
        } catch (RuntimeException ignored) {
            // URL delivery must never prevent the existing swipe controls from starting.
            started = false;
        }
    }

    static String getCachedUrl(SharedPreferences prefs) {
        String url = prefs.getString(SwipeSettings.KEY_SHARED_URL, "");
        return isValidHttpUrl(url) ? url.trim() : "";
    }

    static boolean open(Context context, String url) {
        if (!isValidHttpUrl(url)) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    static String displayLabel(String url) {
        if (!isValidHttpUrl(url)) {
            return "";
        }
        Uri uri = Uri.parse(url.trim());
        String host = uri.getHost();
        if (!TextUtils.isEmpty(host)) {
            return host;
        }
        return url.length() > 36 ? url.substring(0, 33) + "..." : url;
    }

    static boolean isValidHttpUrl(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        String url = value.trim();
        if (url.length() > MAX_URL_LENGTH) {
            return false;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || TextUtils.isEmpty(host)) {
            return false;
        }
        String normalizedScheme = scheme.toLowerCase(Locale.US);
        return "http".equals(normalizedScheme) || "https".equals(normalizedScheme);
    }
}
