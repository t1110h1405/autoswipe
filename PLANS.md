# Shared URL rollout plan

## Goal

Allow an authorized administrator to set one shared HTTP(S) URL from a web page. Every installed Auto Swipe app can display the latest URL in the app and floating panel, and opens it only after the user taps it.

## Scope

- Firebase Realtime Database stores the current shared URL and update timestamp.
- A Firebase Hosting admin page uses Google sign-in and can update the URL.
- Database rules allow public read access only to the shared URL and restrict writes to the administrator account.
- The Android app listens for URL updates, caches the latest valid value, and displays a clickable button in both the main screen and accessibility overlay.
- Only `http://` and `https://` URLs are accepted.
- No notifications and no automatic browser launch.

## Steps

- [x] Review the existing Android app and choose the minimum Firebase architecture.
- [x] Authenticate the Firebase CLI and create or select the Firebase project.
- [x] Configure the Android app, Realtime Database, Authentication, Hosting, and security rules.
- [x] Implement and verify the administrator page.
- [x] Implement Android URL synchronization and buttons.
- [ ] Build the signed APK, deploy the admin page, and publish the release.

## Verification

- An unauthorized browser cannot update the shared URL.
- The authorized administrator can set and clear a valid URL.
- Invalid schemes such as `javascript:` are rejected by the page, rules, and Android app.
- Two app instances receive the same URL.
- Tapping the in-app or overlay button opens the URL; receiving it never opens a screen automatically.
- Existing swipe, tap, timer, lock, and target-picker behavior remains intact.
