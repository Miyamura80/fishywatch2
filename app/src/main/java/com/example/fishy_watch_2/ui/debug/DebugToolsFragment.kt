package com.example.fishy_watch_2.ui.debug

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.fishy_watch_2.CallDetector
import com.example.fishy_watch_2.CallType
import com.example.fishy_watch_2.OverlayService
import com.example.fishy_watch_2.PersistentNotificationService
import com.example.fishy_watch_2.R
import com.example.fishy_watch_2.databinding.FragmentDebugToolsBinding
import java.util.concurrent.Executors
import android.app.AlertDialog
import com.example.fishy_watch_2.ContactManager

class DebugToolsFragment : Fragment() {

    companion object {
        private const val TAG = "DebugToolsFragment"
        private const val DEFAULT_COUNTDOWN_SECONDS = 5
        private const val DEFAULT_VOICE_COUNTDOWN_SECONDS = 3
    }

    private var _binding: FragmentDebugToolsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var phonePermissionLauncher: ActivityResultLauncher<String>
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var voiceCountdownRunnable: Runnable? = null
    
    // Call detection
    private lateinit var callDetector: CallDetector
    private val callUpdateExecutor = Executors.newSingleThreadExecutor()

    // UI elements
    private lateinit var btnTestImmediate: Button
    private lateinit var btnTestCountdown: Button
    private lateinit var btnTestVoiceImmediate: Button
    private lateinit var btnTestVoiceCountdown: Button
    private lateinit var btnRequestPermissions: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var btnToggleAutoDetection: Button
    private lateinit var btnToggleProtection: Button
    private lateinit var etCountdownSeconds: EditText
    private lateinit var etVoiceCountdownSeconds: EditText
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvTtsStatus: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var btnClearAllContacts: Button
    
    // Auto-detection state
    private var autoDetectionEnabled = false
    private var protectionEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callDetector = CallDetector(requireContext())
        setupPermissionLaunchers()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugToolsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initializeViews()
        setupButtons()
        setupCallDetection()
        updateSystemStatus()
        
        return root
    }

    override fun onResume() {
        super.onResume()
        updateSystemStatus()
        // Also update call status when resuming to catch any changes
        updateCallStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel any pending countdowns
        countdownRunnable?.let { 
            Log.d(TAG, "Cancelling pending deepfake countdown on destroy")
            handler.removeCallbacks(it) 
        }
        voiceCountdownRunnable?.let { 
            Log.d(TAG, "Cancelling pending voice countdown on destroy")
            handler.removeCallbacks(it) 
        }
        
        // Clean up call detection executor
        try {
            callUpdateExecutor.shutdown()
            Log.d(TAG, "Call detection executor shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down call detection executor: ${e.message}", e)
        }
        
        _binding = null
    }

    private fun initializeViews() {
        btnTestImmediate = binding.root.findViewById(R.id.btnTestImmediate)
        btnTestCountdown = binding.root.findViewById(R.id.btnTestCountdown)
        btnTestVoiceImmediate = binding.root.findViewById(R.id.btnTestVoiceImmediate)
        btnTestVoiceCountdown = binding.root.findViewById(R.id.btnTestVoiceCountdown)
        btnRequestPermissions = binding.root.findViewById(R.id.btnRequestPermissions)
        btnOpenSettings = binding.root.findViewById(R.id.btnOpenSettings)
        etCountdownSeconds = binding.root.findViewById(R.id.etCountdownSeconds)
        etVoiceCountdownSeconds = binding.root.findViewById(R.id.etVoiceCountdownSeconds)
        tvOverlayStatus = binding.root.findViewById(R.id.tvOverlayStatus)
        tvNotificationStatus = binding.root.findViewById(R.id.tvNotificationStatus)
        tvTtsStatus = binding.root.findViewById(R.id.tvTtsStatus)
        tvCallStatus = binding.root.findViewById(R.id.tvCallStatus)
        btnToggleAutoDetection = binding.root.findViewById(R.id.btnToggleAutoDetection)
        btnToggleProtection = binding.root.findViewById(R.id.btnToggleProtection)
        btnClearAllContacts = binding.root.findViewById(R.id.btnClearAllContacts)
    }

    private fun setupPermissionLaunchers() {
        // Overlay permission launcher
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            updateSystemStatus()
            if (Settings.canDrawOverlays(requireContext())) {
                Log.d(TAG, "Overlay permission granted")
                Toast.makeText(requireContext(), "Overlay permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Overlay permission denied")
                Toast.makeText(requireContext(), "Overlay permission is required for deepfake alerts", Toast.LENGTH_LONG).show()
            }
        }

        // Notification permission launcher (Android 13+)
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            updateSystemStatus()
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
                Toast.makeText(requireContext(), "Notification permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Notification permission denied")
                Toast.makeText(requireContext(), "Notification permission recommended for alerts", Toast.LENGTH_LONG).show()
            }
        }

        // Phone state permission launcher
        phonePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            updateSystemStatus()
            if (isGranted) {
                Log.d(TAG, "Phone state permission granted")
                Toast.makeText(requireContext(), "Phone state permission granted for call detection!", Toast.LENGTH_SHORT).show()
                setupCallDetection()
            } else {
                Log.w(TAG, "Phone state permission denied")
                Toast.makeText(requireContext(), "Phone permission recommended for enhanced call detection", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupButtons() {
        btnTestImmediate.setOnClickListener {
            Log.d(TAG, "Immediate test button clicked")
            if (Settings.canDrawOverlays(requireContext())) {
                Log.d(TAG, "Overlay permission confirmed - showing immediate deepfake alert")
                showOverlay("fishy.watch: IMMEDIATE TEST - This call exhibits multiple signs of a deepfake phishing attack. Proceed with extreme caution.")
            } else {
                Log.w(TAG, "Overlay permission not granted - requesting permission")
                Toast.makeText(requireContext(), "Please grant overlay permission for deepfake alerts", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
            }
        }

        btnTestCountdown.setOnClickListener {
            Log.d(TAG, "Countdown button clicked")
            if (Settings.canDrawOverlays(requireContext())) {
                Log.d(TAG, "Overlay permission confirmed - starting deepfake alert countdown")
                startConfigurableCountdown()
            } else {
                Log.w(TAG, "Overlay permission not granted - requesting permission")
                Toast.makeText(requireContext(), "Please grant overlay permission for deepfake alerts", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
            }
        }

        btnRequestPermissions.setOnClickListener {
            requestAllPermissions()
        }

        btnTestVoiceImmediate.setOnClickListener {
            Log.d(TAG, "Immediate voice test button clicked")
            if (Settings.canDrawOverlays(requireContext())) {
                Log.d(TAG, "Overlay permission confirmed - showing immediate voice signature alert")
                showVoiceOverlay("fishy.watch: Your voice signature was detected calling on the device owned by 'Mom', but there seems to be a contextual discrepancy. Please authenticate this was you calling by pressing this button.")
            } else {
                Log.w(TAG, "Overlay permission not granted - requesting permission")
                Toast.makeText(requireContext(), "Please grant overlay permission for voice alerts", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
            }
        }

        btnTestVoiceCountdown.setOnClickListener {
            Log.d(TAG, "Voice countdown button clicked")
            if (Settings.canDrawOverlays(requireContext())) {
                Log.d(TAG, "Overlay permission confirmed - starting voice signature alert countdown")
                startVoiceCountdown()
            } else {
                Log.w(TAG, "Overlay permission not granted - requesting permission")
                Toast.makeText(requireContext(), "Please grant overlay permission for voice alerts", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
            }
        }

        btnOpenSettings.setOnClickListener {
            openAppSettings()
        }

        btnToggleAutoDetection.setOnClickListener {
            toggleAutoDetection()
        }

        btnToggleProtection.setOnClickListener {
            togglePersistentProtection()
        }

        btnClearAllContacts.setOnClickListener {
            showClearAllContactsDialog()
        }
    }

    private fun updateSystemStatus() {
        // Update overlay permission status
        if (Settings.canDrawOverlays(requireContext())) {
            tvOverlayStatus.text = "✅ Granted"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        } else {
            tvOverlayStatus.text = "❌ Denied"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        }

        // Update notification permission status
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Automatically granted on older versions
        }

        if (hasNotificationPermission) {
            tvNotificationStatus.text = "✅ Granted"
            tvNotificationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        } else {
            tvNotificationStatus.text = "❌ Denied"
            tvNotificationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        }

        // Update TTS status (always show as ready for now)
        tvTtsStatus.text = "🔊 Ready"
        tvTtsStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))

        // Update call status
        updateCallStatus()
    }

    private fun setupCallDetection() {
        try {
            // Register for call state changes on supported devices
            callDetector.registerCallStateListener(callUpdateExecutor) { newCallType ->
                Log.d(TAG, "Call state changed to: $newCallType")
                // Update UI on main thread
                handler.post {
                    updateCallStatus()
                }
            }
            Log.d(TAG, "Call detection setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up call detection: ${e.message}", e)
        }
    }

    private fun updateCallStatus() {
        try {
            val callStatus = callDetector.getCallStatusDescription()
            tvCallStatus.text = callStatus
            
            // Set color based on call state
            val callType = callDetector.getCurrentCallType()
            val color = when (callType) {
                CallType.CELLULAR -> ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
                CallType.VOIP -> ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                CallType.NONE -> ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            }
            tvCallStatus.setTextColor(color)
            
            Log.d(TAG, "Call status updated: $callStatus")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating call status: ${e.message}", e)
            tvCallStatus.text = "❓ Error"
            tvCallStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        }
    }

    private fun getCountdownSeconds(): Int {
        return try {
            val text = etCountdownSeconds.text.toString()
            if (text.isBlank()) {
                DEFAULT_COUNTDOWN_SECONDS
            } else {
                val seconds = text.toInt()
                if (seconds < 1) 1 else if (seconds > 60) 60 else seconds
            }
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Invalid countdown seconds input, using default")
            DEFAULT_COUNTDOWN_SECONDS
        }
    }

    private fun startConfigurableCountdown() {
        val countdownSeconds = getCountdownSeconds()
        Log.d(TAG, "Starting $countdownSeconds-second deepfake alert countdown")
        Toast.makeText(requireContext(), "⚠️ Deepfake alert will appear in $countdownSeconds seconds...", Toast.LENGTH_SHORT).show()
        
        // Cancel any existing countdown
        countdownRunnable?.let { 
            Log.d(TAG, "Cancelling existing countdown")
            handler.removeCallbacks(it) 
        }
        
        // Create new countdown runnable
        countdownRunnable = Runnable {
            Log.d(TAG, "$countdownSeconds-second countdown completed - showing DEEPFAKE ALERT")
            try {
                showOverlay("fishy.watch: This call exhibits multiple signs of a deepfake phishing attack. Proceed with extreme caution.")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing deepfake alert after countdown", e)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error showing deepfake alert: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // Schedule the overlay to appear after configured seconds
        handler.postDelayed(countdownRunnable!!, countdownSeconds * 1000L)
        
        // Show countdown progress
        showCountdownProgress(countdownSeconds)
    }

    private fun showCountdownProgress(totalSeconds: Int) {
        var secondsLeft = totalSeconds
        
        val progressRunnable = object : Runnable {
            override fun run() {
                if (secondsLeft > 0) {
                    Log.d(TAG, "Deepfake alert countdown: $secondsLeft seconds remaining")
                    secondsLeft--
                    handler.postDelayed(this, 1000) // Update every second
                }
            }
        }
        
        handler.post(progressRunnable)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(requireContext())) {
            val uri = Uri.parse("package:${requireContext().packageName}")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestPhonePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Manifest.permission.READ_BASIC_PHONE_STATE
        } else {
            Manifest.permission.READ_PHONE_STATE
        }
        
        if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
            phonePermissionLauncher.launch(permission)
        } else {
            Log.d(TAG, "Phone permission already granted")
        }
    }

    private fun requestAllPermissions() {
        Log.d(TAG, "Requesting all permissions")
        
        if (!Settings.canDrawOverlays(requireContext())) {
            requestOverlayPermission()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission()
        } else {
            requestPhonePermission()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun showOverlay(message: String) {
        Log.d(TAG, "showOverlay called with deepfake message: $message")
        
        // Double-check overlay permission
        if (!Settings.canDrawOverlays(requireContext())) {
            Log.e(TAG, "Cannot show deepfake alert - permission not granted")
            Toast.makeText(requireContext(), "Overlay permission lost - please re-grant for deepfake protection", Toast.LENGTH_LONG).show()
            return
        }
        
        try {
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("overlay_message", message)
            
            Log.d(TAG, "Starting OverlayService for DEEPFAKE ALERT with intent: $intent")
            val result = ContextCompat.startForegroundService(requireContext(), intent)
            Log.d(TAG, "startForegroundService result: $result")
            
            if (result == null) {
                Log.e(TAG, "Failed to start OverlayService for deepfake alert - result is null")
                Toast.makeText(requireContext(), "Failed to start deepfake alert service", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting deepfake alert service", e)
            
            // Check if it's a background service start restriction
            if (e.message?.contains("ForegroundServiceStartNotAllowedException") == true || 
                e.message?.contains("mAllowStartForeground false") == true) {
                Log.w(TAG, "Cannot start foreground service from background - app likely in background")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), 
                        "⚠️ App is in background! For testing, keep fishy.watch in foreground during countdown.", 
                        Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Error starting deepfake alert: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Voice Signature Testing Methods
    private fun getVoiceCountdownSeconds(): Int {
        return try {
            val text = etVoiceCountdownSeconds.text.toString()
            if (text.isBlank()) {
                DEFAULT_VOICE_COUNTDOWN_SECONDS
            } else {
                val seconds = text.toInt()
                if (seconds < 1) 1 else if (seconds > 60) 60 else seconds
            }
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Invalid voice countdown seconds input, using default")
            DEFAULT_VOICE_COUNTDOWN_SECONDS
        }
    }

    private fun startVoiceCountdown() {
        val countdownSeconds = getVoiceCountdownSeconds()
        Log.d(TAG, "Starting $countdownSeconds-second voice signature alert countdown")
        Toast.makeText(requireContext(), "🎤 Voice authentication alert will appear in $countdownSeconds seconds...", Toast.LENGTH_SHORT).show()
        
        // Cancel any existing voice countdown
        voiceCountdownRunnable?.let { 
            Log.d(TAG, "Cancelling existing voice countdown")
            handler.removeCallbacks(it) 
        }
        
        // Create new voice countdown runnable
        voiceCountdownRunnable = Runnable {
            Log.d(TAG, "$countdownSeconds-second voice countdown completed - showing VOICE SIGNATURE ALERT")
            try {
                showVoiceOverlay("fishy.watch: Your voice signature was detected calling on the device owned by 'Mom', but there seems to be a contextual discrepancy. Please authenticate this was you calling by pressing this button.")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing voice signature alert after countdown", e)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error showing voice alert: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // Schedule the overlay to appear after configured seconds
        handler.postDelayed(voiceCountdownRunnable!!, countdownSeconds * 1000L)
        
        // Show countdown progress
        showVoiceCountdownProgress(countdownSeconds)
    }

    private fun showVoiceCountdownProgress(totalSeconds: Int) {
        var secondsLeft = totalSeconds
        
        val progressRunnable = object : Runnable {
            override fun run() {
                if (secondsLeft > 0) {
                    Log.d(TAG, "Voice signature alert countdown: $secondsLeft seconds remaining")
                    secondsLeft--
                    handler.postDelayed(this, 1000) // Update every second
                }
            }
        }
        
        handler.post(progressRunnable)
    }

    private fun showVoiceOverlay(message: String) {
        Log.d(TAG, "showVoiceOverlay called with voice signature message: $message")
        
        // Double-check overlay permission
        if (!Settings.canDrawOverlays(requireContext())) {
            Log.e(TAG, "Cannot show voice signature alert - permission not granted")
            Toast.makeText(requireContext(), "Overlay permission lost - please re-grant for voice authentication", Toast.LENGTH_LONG).show()
            return
        }
        
        try {
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("overlay_message", message)
            intent.putExtra("overlay_type", "voice_signature") // Add a type identifier
            
            Log.d(TAG, "Starting OverlayService for VOICE SIGNATURE ALERT with intent: $intent")
            val result = ContextCompat.startForegroundService(requireContext(), intent)
            Log.d(TAG, "startForegroundService result: $result")
            
            if (result == null) {
                Log.e(TAG, "Failed to start OverlayService for voice signature alert - result is null")
                Toast.makeText(requireContext(), "Failed to start voice signature alert service", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting voice signature alert service", e)
            
            // Check if it's a background service start restriction
            if (e.message?.contains("ForegroundServiceStartNotAllowedException") == true || 
                e.message?.contains("mAllowStartForeground false") == true) {
                Log.w(TAG, "Cannot start foreground service from background - app likely in background")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), 
                        "⚠️ App is in background! For testing, keep fishy.watch in foreground during countdown.", 
                        Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Error starting voice signature alert: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleAutoDetection() {
        autoDetectionEnabled = !autoDetectionEnabled
        
        if (autoDetectionEnabled) {
            Log.d(TAG, "Enabling automatic deepfake detection during calls")
            btnToggleAutoDetection.text = "🔴 Disable Auto-Detection"
            btnToggleAutoDetection.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            
            // Enable automatic detection
            callDetector.enableAutomaticDetection(requireContext()) { callType ->
                Log.w(TAG, "Auto-detection triggered deepfake alert during $callType call!")
                
                // Show overlay on main thread
                handler.post {
                    val message = when (callType) {
                        CallType.CELLULAR -> "fishy.watch: AUTO-DETECTED deepfake during cellular call. This call exhibits multiple signs of a deepfake phishing attack."
                        CallType.VOIP -> "fishy.watch: AUTO-DETECTED deepfake during VoIP call. This call exhibits multiple signs of a deepfake phishing attack."
                        else -> "fishy.watch: AUTO-DETECTED deepfake during unknown call type."
                    }
                    
                    if (Settings.canDrawOverlays(requireContext())) {
                        showOverlay(message)
                    } else {
                        Toast.makeText(requireContext(), "🚨 DEEPFAKE DETECTED! $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            Toast.makeText(requireContext(), "🤖 Auto-detection ENABLED - Start a call to test!", Toast.LENGTH_LONG).show()
            
        } else {
            Log.d(TAG, "Disabling automatic deepfake detection")
            btnToggleAutoDetection.text = "🔄 Enable Auto-Detection"
            btnToggleAutoDetection.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            
            Toast.makeText(requireContext(), "🔄 Auto-detection DISABLED", Toast.LENGTH_SHORT).show()
            
            // Note: In a real implementation, you'd want to properly disable the listener
            // For this demo, the detection will continue until the fragment is destroyed
        }
    }

    private fun togglePersistentProtection() {
        protectionEnabled = !protectionEnabled
        
        if (protectionEnabled) {
            Log.d(TAG, "Starting persistent protection notification")
            btnToggleProtection.text = "🔴 Stop Protection"
            btnToggleProtection.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            
            try {
                PersistentNotificationService.startProtection(requireContext())
                Toast.makeText(requireContext(), "🛡️ Protection ENABLED - Persistent notification active!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting persistent protection: ${e.message}", e)
                Toast.makeText(requireContext(), "Error starting protection: ${e.message}", Toast.LENGTH_LONG).show()
                
                // Reset state on error
                protectionEnabled = false
                btnToggleProtection.text = "🛡️ Start Protection"
                btnToggleProtection.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
            }
            
        } else {
            Log.d(TAG, "Stopping persistent protection notification")
            btnToggleProtection.text = "🛡️ Start Protection"
            btnToggleProtection.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
            
            try {
                PersistentNotificationService.stopProtection(requireContext())
                Toast.makeText(requireContext(), "🔄 Protection DISABLED", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping persistent protection: ${e.message}", e)
                Toast.makeText(requireContext(), "Error stopping protection: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showClearAllContactsDialog() {
        val contacts = ContactManager.getTrustedContacts(requireContext())
        
        if (contacts.isEmpty()) {
            Toast.makeText(requireContext(), "No trusted contacts to clear", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All Trusted Contacts")
            .setMessage("Are you sure you want to delete all ${contacts.size} trusted contacts?\n\nThis will permanently remove:\n• All contact information\n• All voice signatures\n• All pairing history\n\nThis action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                clearAllContacts()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    
    private fun clearAllContacts() {
        try {
            ContactManager.clearAllContacts(requireContext())
            Toast.makeText(requireContext(), "🗑️ All trusted contacts cleared successfully", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Successfully cleared all trusted contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all contacts: ${e.message}", e)
            Toast.makeText(requireContext(), "❌ Error clearing contacts: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
} 