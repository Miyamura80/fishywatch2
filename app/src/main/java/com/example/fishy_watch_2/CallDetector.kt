package com.example.fishy_watch_2

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.random.Random

enum class CallType { NONE, CELLULAR, VOIP }

class CallDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "CallDetector"
    }

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Get the current call type based on telephony and audio manager state
     */
    fun getCurrentCallType(): CallType {
        return try {
            Log.d(TAG, "Checking current call type...")
            
            // 1. Check cellular state first
            val callState = telephonyManager.callState
            Log.d(TAG, "Telephony call state: $callState")
            
            if (callState != TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Detected CELLULAR call")
                return CallType.CELLULAR
            }

            // 2. Check VoIP via audio mode
            val audioMode = audioManager.mode
            Log.d(TAG, "Audio mode: $audioMode")
            
            val result = when (audioMode) {
                AudioManager.MODE_IN_CALL -> {
                    Log.d(TAG, "Detected CELLULAR call via audio mode (fallback)")
                    CallType.CELLULAR   // rare fall-back
                }
                AudioManager.MODE_IN_COMMUNICATION -> {
                    Log.d(TAG, "Detected VOIP call")
                    CallType.VOIP
                }
                else -> {
                    Log.d(TAG, "No call detected")
                    CallType.NONE
                }
            }
            
            result
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied when checking call state: ${e.message}")
            // Fallback to audio-only detection
            val audioMode = audioManager.mode
            when (audioMode) {
                AudioManager.MODE_IN_CALL, 
                AudioManager.MODE_IN_COMMUNICATION -> CallType.VOIP
                else -> CallType.NONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting call type: ${e.message}", e)
            CallType.NONE
        }
    }

    /**
     * Register for live call state updates (API 31+)
     */
    fun registerCallStateListener(executor: Executor, callback: (CallType) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.addOnModeChangedListener(executor) { mode ->
                    Log.d(TAG, "Audio mode changed to: $mode")
                    val newCallType = getCurrentCallType()
                    callback(newCallType)
                }
                Log.d(TAG, "Registered audio mode change listener (API 31+)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register audio mode listener: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Audio mode change listener not available (API < 31)")
        }
    }

    /**
     * Get a human-readable description of the current call state
     */
    fun getCallStatusDescription(): String {
        return when (getCurrentCallType()) {
            CallType.CELLULAR -> "📞 In Cellular Call"
            CallType.VOIP -> "💬 In VoIP Call"
            CallType.NONE -> "📵 No Active Call"
        }
    }

    /**
     * Check if any call is currently active
     */
    fun isInCall(): Boolean {
        return getCurrentCallType() != CallType.NONE
    }

    /**
     * Get detailed call state information for debugging
     */
    fun getDetailedCallInfo(): String {
        return try {
            val callState = telephonyManager.callState
            val audioMode = audioManager.mode
            val callType = getCurrentCallType()
            
            val callStateStr = when (callState) {
                TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                else -> "UNKNOWN($callState)"
            }
            
            val audioModeStr = when (audioMode) {
                AudioManager.MODE_NORMAL -> "NORMAL"
                AudioManager.MODE_RINGTONE -> "RINGTONE"
                AudioManager.MODE_IN_CALL -> "IN_CALL"
                AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
                else -> "UNKNOWN($audioMode)"
            }
            
            "Call State: $callStateStr | Audio Mode: $audioModeStr | Detected: $callType"
        } catch (e: Exception) {
            "Error getting call info: ${e.message}"
        }
    }

    /**
     * Set up automatic deepfake detection during calls
     * This would work in practice because active calls give foreground context
     */
    fun enableAutomaticDetection(
        context: Context,
        onDeepfakeDetected: (callType: CallType) -> Unit
    ) {
        registerCallStateListener(Executors.newSingleThreadExecutor()) { newCallType ->
            Log.d(TAG, "Call state changed to: $newCallType")
            
            when (newCallType) {
                CallType.CELLULAR, CallType.VOIP -> {
                    Log.d(TAG, "Call active - checking for deepfake indicators...")
                    
                    // In real implementation, this would:
                    // 1. Analyze call audio for deepfake signatures
                    // 2. Check caller ID against known contacts
                    // 3. Apply ML models for voice authentication
                    // 4. Consult cloud services if available
                    
                    // For demo purposes, simulate detection logic
                    val isDeepfakeSuspected = simulateDeepfakeDetection(newCallType)
                    
                    if (isDeepfakeSuspected) {
                        Log.w(TAG, "DEEPFAKE DETECTED during ${newCallType} call!")
                        onDeepfakeDetected(newCallType)
                    }
                }
                CallType.NONE -> {
                    Log.d(TAG, "Call ended - deepfake monitoring stopped")
                }
            }
        }
    }
    
    /**
     * Simulate deepfake detection logic
     * In real implementation, this would use actual ML models and analysis
     */
    private fun simulateDeepfakeDetection(callType: CallType): Boolean {
        // Simulate various detection mechanisms:
        
        // 1. Voice analysis (would analyze audio stream)
        val voiceAnomalyScore = Random.nextDouble() // 0.0 to 1.0
        
        // 2. Caller behavior patterns
        val behaviorAnomalyScore = Random.nextDouble()
        
        // 3. Context analysis (time, location, etc.)
        val contextAnomalyScore = Random.nextDouble()
        
        // Combined risk score
        val riskScore = (voiceAnomalyScore + behaviorAnomalyScore + contextAnomalyScore) / 3.0
        
        // Threshold for triggering alert (adjust based on testing)
        val alertThreshold = 0.7
        
        Log.d(TAG, "Deepfake risk analysis: voice=$voiceAnomalyScore, behavior=$behaviorAnomalyScore, context=$contextAnomalyScore, total=$riskScore")
        
        return riskScore > alertThreshold
    }
} 