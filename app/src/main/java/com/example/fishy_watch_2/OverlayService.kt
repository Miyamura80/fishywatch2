package com.example.fishy_watch_2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
import android.view.WindowManager.LayoutParams.MATCH_PARENT
import android.view.WindowManager.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class OverlayService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "overlay_channel"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false
    private var pendingMessage: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate() called")
        
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Foreground service started successfully")
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "WindowManager obtained successfully")
            
            // Initialize TextToSpeech
            textToSpeech = TextToSpeech(this, this)
            Log.d(TAG, "TextToSpeech initialization started")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate()", e)
            stopSelf()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported for TTS")
                ttsInitialized = false
            } else {
                Log.d(TAG, "TextToSpeech initialized successfully")
                ttsInitialized = true
                // Set speech rate and pitch for more urgent delivery
                textToSpeech?.setSpeechRate(1.0f)
                textToSpeech?.setPitch(1.1f) // Slightly higher pitch for attention
                
                // Speak any pending message
                pendingMessage?.let { storedData ->
                    Log.d(TAG, "Speaking pending message now that TTS is ready")
                    val parts = storedData.split("|", limit = 2)
                    if (parts.size == 2) {
                        val overlayType = parts[0]
                        val message = parts[1]
                        speakWarningNow(message, overlayType)
                    } else {
                        // Fallback for old format
                        speakWarningNow(storedData, "deepfake")
                    }
                    pendingMessage = null
                }
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
            ttsInitialized = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand() called with intent: $intent")
        
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted!")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Get the message and type from the intent (if any)
        val message = intent?.getStringExtra("overlay_message") 
            ?: "fishy.watch: This call exhibits multiple signs of a deepfake phishing attack. Proceed with extreme caution."
        val overlayType = intent?.getStringExtra("overlay_type") ?: "deepfake"
        Log.d(TAG, "Overlay message: $message")
        Log.d(TAG, "Overlay type: $overlayType")
        
        try {
            showOverlay(message, overlayType)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand showing overlay", e)
            stopSelf()
        }
        
        return START_NOT_STICKY // Don't restart if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy() called")
        hideOverlay()
        
        // Clean up TextToSpeech
        textToSpeech?.let { tts ->
            if (tts.isSpeaking) {
                tts.stop()
            }
            tts.shutdown()
            Log.d(TAG, "TextToSpeech cleaned up")
        }
        textToSpeech = null
        ttsInitialized = false
        pendingMessage = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Deepfake Alert Service",
                IMPORTANCE_LOW
            ).apply {
                description = "Shows deepfake phishing alerts"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("fishy.watch")
            .setContentText("Deepfake alert active")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay(message: String, overlayType: String = "deepfake") {
        Log.d(TAG, "showOverlay() called with message: $message, type: $overlayType")
        
        if (overlayView != null) {
            Log.d(TAG, "Overlay already showing, updating message")
            updateOverlayMessage(message, overlayType)
            return
        }

        try {
            Log.d(TAG, "Inflating overlay layout...")
            // Inflate the overlay layout
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)
            Log.d(TAG, "Layout inflated successfully")

            // Set up the window parameters - full width at top of screen
            val params = WindowManager.LayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                FLAG_NOT_FOCUSABLE or FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 0 // Top of screen
            }
            
            Log.d(TAG, "Window parameters configured: type=${params.type}, flags=${params.flags}, width=MATCH_PARENT")

            // Set the message and customize UI based on overlay type
            overlayView?.findViewById<TextView>(R.id.tvMessage)?.let { textView ->
                textView.text = message
                Log.d(TAG, "Message set on TextView: $message")
            } ?: Log.e(TAG, "Could not find tvMessage TextView!")

            // Customize overlay appearance based on type
            customizeOverlayForType(overlayType)

            // Set up close button
            overlayView?.findViewById<View>(R.id.btnClose)?.setOnClickListener {
                Log.d(TAG, "Close button clicked")
                stopSelf()
            } ?: Log.e(TAG, "Could not find btnClose button!")

            // Set up end call button (new red button)
            overlayView?.findViewById<View>(R.id.btnEndCall)?.setOnClickListener {
                val buttonAction = if (overlayType == "voice_signature") "Confirm Its Me" else "End Call"
                Log.d(TAG, "$buttonAction button clicked")
                // In a real implementation, this would either end the call or confirm identity
                stopSelf()
            } ?: Log.e(TAG, "Could not find btnEndCall button!")

            // Set up learn more button (was action button)
            overlayView?.findViewById<View>(R.id.btnAction)?.setOnClickListener {
                Log.d(TAG, "Learn More button clicked")
                // In a real implementation, this would open fishy.watch website or info
                stopSelf()
            } ?: Log.e(TAG, "Could not find btnAction button!")

            // Add the view to window manager
            Log.d(TAG, "Adding view to WindowManager...")
            windowManager.addView(overlayView, params)
            Log.d(TAG, "RED WARNING BANNER shown successfully!")
            
            // Speak the warning message
            speakWarning(message, overlayType)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
            Log.e(TAG, "Exception details: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateOverlayMessage(message: String, overlayType: String = "deepfake") {
        Log.d(TAG, "Updating overlay message to: $message")
        overlayView?.findViewById<TextView>(R.id.tvMessage)?.text = message
        // Also speak the updated message
        speakWarning(message, overlayType)
    }

    private fun customizeOverlayForType(overlayType: String) {
        Log.d(TAG, "Customizing overlay for type: $overlayType")
        
        when (overlayType) {
            "voice_signature" -> {
                // Change button text to "CONFIRM ITS ME"
                overlayView?.findViewById<Button>(R.id.btnEndCall)?.let { button ->
                    button.text = "CONFIRM ITS ME"
                    Log.d(TAG, "Updated button text to 'CONFIRM ITS ME'")
                } ?: Log.e(TAG, "Could not find btnEndCall button to update text!")
                
                // Update the title TextView - search for the one with DEEPFAKE text
                try {
                    // Find TextView with DEEPFAKE text (it doesn't have an ID)
                    val rootGroup = overlayView as? ViewGroup
                    val titleTextView = findTextViewWithText(rootGroup, "DEEPFAKE", "🐠")
                    
                    titleTextView?.let { textView ->
                        val oldText = textView.text.toString()
                        textView.text = "🎤 VOICE SIGNATURE"
                        Log.d(TAG, "Updated title TextView from '$oldText' to '🎤 VOICE SIGNATURE'")
                    } ?: Log.w(TAG, "Could not find title TextView to update")
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update title TextView: ${e.message}")
                }
                
                // Change background color to orange theme
                try {
                    // Update the main overlay background (root LinearLayout)
                    overlayView?.setBackgroundColor(Color.parseColor("#FF9800"))
                    
                    // Also update the inner LinearLayout background that has the red color
                    val rootGroup = overlayView as? ViewGroup
                    val innerLayout = rootGroup?.getChildAt(0) as? LinearLayout
                    innerLayout?.setBackgroundColor(Color.parseColor("#FF9800"))
                    
                    Log.d(TAG, "Updated background to orange theme for voice signature")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update background color: ${e.message}")
                }
            }
            else -> {
                // Default deepfake styling - ensure button says "END CALL"
                overlayView?.findViewById<Button>(R.id.btnEndCall)?.let { button ->
                    button.text = "END CALL"
                    Log.d(TAG, "Ensured button text is 'END CALL' for deepfake alert")
                }
                Log.d(TAG, "Using default deepfake styling")
            }
        }
    }

    private fun findTextViewWithText(viewGroup: ViewGroup?, vararg searchTexts: String): TextView? {
        if (viewGroup == null) return null
        
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) {
                val text = child.text.toString()
                for (searchText in searchTexts) {
                    if (text.contains(searchText)) {
                        return child
                    }
                }
            } else if (child is ViewGroup) {
                // Recursively search in child ViewGroups
                val found = findTextViewWithText(child, *searchTexts)
                if (found != null) return found
            }
        }
        return null
    }

    private fun speakWarning(message: String, overlayType: String = "deepfake") {
        if (ttsInitialized && textToSpeech != null) {
            speakWarningNow(message, overlayType)
        } else {
            Log.d(TAG, "TextToSpeech not ready yet, queuing message: $message")
            pendingMessage = "$overlayType|$message" // Store both type and message
        }
    }
    
    private fun speakWarningNow(message: String, overlayType: String = "deepfake") {
        Log.d(TAG, "Speaking warning message: $message, type: $overlayType")
        
        // Create appropriate spoken message based on type
        val spokenMessage = when (overlayType) {
            "voice_signature" -> "VOICE SIGNATURE ALERT! Your voice signature was detected calling on the device owned by Mom, but it seems there is a contextual discrepancy. Please authenticate this was you calling by pressing this button."
            else -> "DEEPFAKE ALERT! This call exhibits multiple signs of a deepfake phishing attack. Proceed with extreme caution."
        }
        
        val utteranceId = when (overlayType) {
            "voice_signature" -> "voice_signature_warning"
            else -> "deepfake_warning"
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.speak(
                spokenMessage,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(spokenMessage, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun hideOverlay() {
        Log.d(TAG, "hideOverlay() called")
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Red warning banner hidden successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay", e)
            } finally {
                overlayView = null
            }
        } ?: Log.d(TAG, "No overlay to hide")
    }
} 