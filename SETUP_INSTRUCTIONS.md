# Fishy Watch 2 - Overlay App Setup Instructions (No Firebase)

This Android app displays overlay alerts on top of other apps. The Firebase integration has been temporarily removed for easier testing and development.

## Building and Installing the App

### 1. Build the Project
```bash
./gradlew build
```

### 2. Install on Your Pixel 6a
- Connect your Pixel 6a via USB
- Enable Developer Options and USB Debugging
- Run:
```bash
./gradlew installDebug
```

### 3. Grant Permissions
When you first launch the app, it will prompt for:
1. **Display over other apps** permission - Grant this
2. **Notification** permission (Android 13+) - Grant this

Both permissions are required for the overlay functionality to work.

## Testing the Overlay

### Method 1: Immediate Overlay Test
1. Launch the app on your device
2. You'll see a **red floating action button (FAB)** with an alert icon in the bottom-right corner
3. Tap the button to trigger an immediate test overlay
4. The overlay should appear instantly on top of the app
5. Tap the close button (X) or the "View Details" button to dismiss the overlay

### Method 2: 5-Second Countdown Test ⏰ (NEW!)
1. Launch the app on your device
2. You'll see a **secondary colored FAB** with a play icon above the red button
3. Tap the countdown button to start a 5-second countdown
4. You'll see a toast message: "Warning will appear in 5 seconds..."
5. **Switch to any other app** (home screen, browser, etc.)
6. After exactly 5 seconds, a warning overlay will appear on top of whatever app you're using
7. The overlay will show: "⚠️ WARNING: 5-second countdown completed!"

### Method 3: Testing Real-World Usage
1. Open the Fishy Watch app
2. Tap the **countdown button** (play icon)
3. Immediately switch to another app (e.g., open Chrome, Calculator, etc.)
4. Continue using the other app normally
5. After 5 seconds, the warning banner will appear over that app
6. Dismiss it and notice you can continue using the app underneath

## App Features

### Two Testing Modes
- **Immediate Test** (red alert button): Shows overlay right away
- **Countdown Test** (blue/secondary play button): Waits 5 seconds, then shows overlay

### Overlay Features
The overlay includes:
- **Modern Material Design card** with shadow and rounded corners
- **Title**: "Fishy Watch Alert"
- **Custom message**: Different messages for immediate vs countdown tests
- **Close button**: X button in the top-right corner
- **Action button**: "View Details" button (currently just dismisses the overlay)

### Background Countdown
- Runs completely in the background
- Works even when you switch apps
- Shows progress in the logs (check Logcat for countdown updates)
- Can be cancelled if the app is closed
- Uses Android's Handler for precise timing

## Architecture Overview

### Key Components

1. **MainActivity.kt**
   - Handles permission requests for overlay and notifications
   - Contains TWO test buttons: immediate and countdown
   - Manages 5-second countdown using Handler and Runnable
   - Provides user feedback with Toast messages

2. **OverlayService.kt**
   - Foreground service that displays the overlay
   - Creates a persistent notification while running
   - Manages the overlay window lifecycle

3. **overlay_view.xml**
   - Layout file defining the overlay UI
   - Material Design card with title, message, and buttons

## Customization

### Changing the Countdown Duration
To modify the 5-second delay:

```kotlin
// In MainActivity.kt, change this constant:
private const val COUNTDOWN_DELAY_MS = 10000L // 10 seconds instead of 5
```

### Changing the Overlay Message
To display a custom message in the overlay:

```kotlin
val intent = Intent(this, OverlayService::class.java)
intent.putExtra("overlay_message", "Your custom message here")
ContextCompat.startForegroundService(this, intent)
```

### Silent Countdown
To remove the countdown progress logs, comment out this line in `startCountdown()`:

```kotlin
// showCountdownProgress() // Comment this out for silent countdown
```

### Modifying the Overlay Appearance
Edit `app/src/main/res/layout/overlay_view.xml` to:
- Change colors, fonts, or sizes
- Add new UI elements
- Modify the layout structure

### Adding Custom Actions
In `OverlayService.kt`, you can modify the action button behavior:

```kotlin
// In the showOverlay method
overlayView?.findViewById<View>(R.id.btnAction)?.setOnClickListener {
    Log.d(TAG, "Action button clicked")
    // Add your custom action here
    // For example: open an activity, send a broadcast, etc.
    stopSelf()
}
```

## Troubleshooting

### App Not Building
1. Make sure all Firebase references have been removed
2. Check that Android SDK is up to date
3. Try cleaning the build: `./gradlew clean build`

### Overlay Not Showing
1. Verify "Display over other apps" permission is granted
2. Check Logcat for error messages
3. Make sure the app is not in battery optimization
4. Try restarting the app

### Countdown Not Working
1. Check Logcat for countdown progress messages
2. Ensure you're not closing the app during countdown
3. Verify overlay permission is granted before starting countdown
4. Try the immediate test first to ensure overlay system works

### Permission Issues
1. Go to Android Settings > Apps > Fishy Watch 2 > Permissions
2. Ensure "Display over other apps" is enabled
3. For Android 13+, ensure "Notifications" permission is granted

## Security Considerations

For production use:
1. Add authentication before allowing overlay triggers
2. Validate any data passed to the overlay service
3. Implement rate limiting to prevent spam
4. Log all overlay events for audit purposes
5. Consider additional gating (e.g., only when device is unlocked)
6. Add cancellation mechanisms for countdown timers

## Adding Firebase Back (Optional)

If you want to add Firebase Cloud Messaging back later:

1. **Add Dependencies**: Uncomment Firebase dependencies in `build.gradle.kts`
2. **Create Firebase Project**: Follow the original Firebase setup steps
3. **Add Configuration**: Place `google-services.json` in the `app/` directory
4. **Restore Service**: Re-add `MyFirebaseService.kt` for handling FCM messages
5. **Update Manifest**: Add FCM service and permissions back

## Testing Checklist

- [ ] App builds without errors
- [ ] App installs on Pixel 6a
- [ ] Both permissions granted on first launch
- [ ] Two test buttons appear (red alert + blue/secondary play)
- [ ] **Immediate test**: Tapping red button shows overlay instantly
- [ ] **Countdown test**: Tapping blue button starts countdown
- [ ] Toast message appears: "Warning will appear in 5 seconds..."
- [ ] Can switch to other apps during countdown
- [ ] Overlay appears after exactly 5 seconds
- [ ] Overlay shows countdown completion message
- [ ] Overlay appears over other apps (not just Fishy Watch)
- [ ] Close button works on both overlay types
- [ ] Action button works
- [ ] Foreground service notification appears during countdown
- [ ] Service stops when overlay is dismissed
- [ ] Multiple countdowns can be started (cancels previous)

## Development Notes

- The overlay uses `TYPE_APPLICATION_OVERLAY` for Android 8+ compatibility
- Foreground service uses `remoteMessaging` type (ready for future FCM integration)
- All logs are tagged for easy filtering in Logcat
- The service is designed to be lightweight and not restart automatically
- Handler ensures countdown continues even when app is backgrounded
- Toast messages provide immediate user feedback
- Countdown can be cancelled by closing the app or starting a new countdown 