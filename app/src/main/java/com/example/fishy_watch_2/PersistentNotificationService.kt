package com.example.fishy_watch_2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class PersistentNotificationService : Service() {

    companion object {
        private const val TAG = "PersistentNotificationService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "fishy_watch_protection"
        
        fun startProtection(context: Context) {
            val intent = Intent(context, PersistentNotificationService::class.java)
            intent.action = "START_PROTECTION"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopProtection(context: Context) {
            val intent = Intent(context, PersistentNotificationService::class.java)
            intent.action = "STOP_PROTECTION"
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PersistentNotificationService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand with action: $action")
        
        when (action) {
            "START_PROTECTION" -> {
                startForegroundProtection()
            }
            "STOP_PROTECTION" -> {
                stopForeground(true)
                stopSelf()
            }
            else -> {
                startForegroundProtection()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "fishy.watch Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing deepfake protection status"
                setShowBadge(false)
                setSound(null, null) // Silent
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun startForegroundProtection() {
        Log.d(TAG, "Starting foreground protection notification")
        
        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            openAppIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐠 fishy.watch Active")
            .setContentText("Deepfake protection enabled • Tap to open")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Cannot be dismissed by swipe
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("fishy.watch deepfake protection is active. Overlay alerts will work from background. Tap to open app."))
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Persistent protection notification started")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PersistentNotificationService destroyed")
    }
} 