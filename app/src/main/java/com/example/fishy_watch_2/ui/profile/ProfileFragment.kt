package com.example.fishy_watch_2.ui.profile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fishy_watch_2.ContactManager
import com.example.fishy_watch_2.NFCCardEmulationService
import com.example.fishy_watch_2.NFCReader
import com.example.fishy_watch_2.R
import com.example.fishy_watch_2.TrustedContact
import com.example.fishy_watch_2.VoiceRecordingActivity
import com.example.fishy_watch_2.databinding.FragmentProfileBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
        private const val PREFS_NAME = "fishy_watch_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_DEVICE_ID = "device_id"
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    // NFC
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    
    // UI Components
    private lateinit var editTextName: TextInputEditText
    private lateinit var textDeviceId: TextView
    private lateinit var buttonSaveProfile: Button
    private lateinit var buttonStartPairing: Button
    private lateinit var textPairingInstructions: TextView
    private lateinit var iconNfcStatus: ImageView
    private lateinit var textNfcStatus: TextView
    private lateinit var recyclerRecentPairings: RecyclerView
    private lateinit var textNoPairings: TextView
    
    // State
    private var isPairingActive = false
    private var deviceId: String = ""
    private var userName: String = ""
    
    // Temporary contact for voice recording
    private var pendingContact: TrustedContact? = null
    
    // Activity result launcher for voice recording
    private val voiceRecordingLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val contactName = data?.getStringExtra(VoiceRecordingActivity.EXTRA_CONTACT_NAME)
            val contactDeviceId = data?.getStringExtra(VoiceRecordingActivity.EXTRA_CONTACT_DEVICE_ID)
            val voiceFilePath = data?.getStringExtra(VoiceRecordingActivity.EXTRA_VOICE_FILE_PATH)
            
            Log.d(TAG, "Voice recording completed for: $contactName")
            
            if (contactName != null && contactDeviceId != null && voiceFilePath != null) {
                // Create final contact with voice signature
                val finalContact = pendingContact?.copy(voiceSignaturePath = voiceFilePath)
                if (finalContact != null) {
                    saveFinalContact(finalContact)
                }
            } else {
                Log.w(TAG, "Missing voice recording data")
                Toast.makeText(requireContext(), "Voice recording failed. Contact not saved.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Voice recording cancelled")
            Toast.makeText(requireContext(), "Voice recording cancelled. Contact not saved.", Toast.LENGTH_SHORT).show()
        }
        
        pendingContact = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        initializeViews()
        setupNFC()
        loadUserProfile()
        setupClickListeners()
        updateNFCStatus()
        
        return root
    }
    
    private fun initializeViews() {
        editTextName = binding.root.findViewById(R.id.editTextName)
        textDeviceId = binding.root.findViewById(R.id.textDeviceId)
        buttonSaveProfile = binding.root.findViewById(R.id.buttonSaveProfile)
        buttonStartPairing = binding.root.findViewById(R.id.buttonStartPairing)
        textPairingInstructions = binding.root.findViewById(R.id.textPairingInstructions)
        iconNfcStatus = binding.root.findViewById(R.id.iconNfcStatus)
        textNfcStatus = binding.root.findViewById(R.id.textNfcStatus)
        recyclerRecentPairings = binding.root.findViewById(R.id.recyclerRecentPairings)
        textNoPairings = binding.root.findViewById(R.id.textNoPairings)
        
        // Setup RecyclerView
        recyclerRecentPairings.layoutManager = LinearLayoutManager(requireContext())
        // TODO: Add adapter for recent pairings
    }
    
    private fun setupNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())
        
        if (nfcAdapter == null) {
            Log.e(TAG, "NFC not supported on this device")
            return
        }
        
        // Create pending intent for NFC
        val intent = Intent(requireContext(), requireActivity()::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            requireContext(), 0, intent,
            PendingIntent.FLAG_MUTABLE
        )
        
        // Setup intent filters for NFC tech discovery (for reading HCE devices)
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        
        intentFiltersArray = arrayOf(techFilter, tagFilter)
    }
    
    private fun loadUserProfile() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load or generate device ID
        deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: generateDeviceId().also { newId ->
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        }
        
        // Load user name
        userName = prefs.getString(KEY_USER_NAME, "") ?: ""
        
        // Update UI
        textDeviceId.text = deviceId
        editTextName.setText(userName)
        
        Log.d(TAG, "Loaded profile - User: $userName, Device: $deviceId")
    }
    
    private fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        return "device_${androidId.take(8)}..."
    }
    
    private fun setupClickListeners() {
        buttonSaveProfile.setOnClickListener {
            saveUserProfile()
        }
        
        buttonStartPairing.setOnClickListener {
            toggleNFCPairing()
        }
    }
    
    private fun saveUserProfile() {
        val newName = editTextName.text.toString().trim()
        
        if (newName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save to SharedPreferences
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_NAME, newName).apply()
        
        userName = newName
        setupCardEmulation()
        
        Toast.makeText(requireContext(), "Profile saved successfully! ✅", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Profile saved - User: $userName, Device: $deviceId")
    }
    
    private fun toggleNFCPairing() {
        if (isPairingActive) {
            stopNFCPairing()
        } else {
            startNFCPairing()
        }
    }
    
    private fun startNFCPairing() {
        if (nfcAdapter == null) {
            Toast.makeText(requireContext(), "NFC not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(requireContext(), "Please enable NFC in Settings", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (userName.isEmpty()) {
            Toast.makeText(requireContext(), "Please save your profile first", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            isPairingActive = true
            setupCardEmulation()
            enableNFCForegroundDispatch()
            
            buttonStartPairing.text = "Stop NFC Pairing"
            textPairingInstructions.text = "🔄 NFC Pairing Active\n\nBring another fishy.watch device close to this phone to pair.\n\nYour device is now discoverable and ready to share contact information."
            
            Toast.makeText(requireContext(), "NFC Pairing started! 📲", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "NFC pairing started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NFC pairing: ${e.message}", e)
            Toast.makeText(requireContext(), "Error starting NFC pairing", Toast.LENGTH_SHORT).show()
            isPairingActive = false
        }
    }
    
    private fun stopNFCPairing() {
        try {
            isPairingActive = false
            disableNFCForegroundDispatch()
            
            buttonStartPairing.text = "Start NFC Pairing"
            textPairingInstructions.text = "🔗 Ready to Pair\n\nTap 'Start NFC Pairing' and bring another fishy.watch device close to exchange contact information securely."
            
            Toast.makeText(requireContext(), "NFC Pairing stopped ⏹️", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "NFC pairing stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping NFC pairing: ${e.message}", e)
        }
    }
    
    private fun setupCardEmulation() {
        // Set our contact data in the HCE service so other devices can read it
        NFCCardEmulationService.contactData = createContactData()
        Log.d(TAG, "Card emulation set up with user data: $userName - $deviceId")
    }
    
    private fun createContactData(): String {
        val contactJson = org.json.JSONObject().apply {
            put("name", userName)
            put("deviceId", deviceId)
            put("timestamp", System.currentTimeMillis())
            put("appVersion", "1.0")
        }
        return contactJson.toString()
    }
    
    private fun enableNFCForegroundDispatch() {
        try {
            nfcAdapter?.enableForegroundDispatch(
                requireActivity(),
                pendingIntent,
                intentFiltersArray,
                arrayOf(arrayOf("android.nfc.tech.IsoDep")) // For reading HCE devices
            )
            Log.d(TAG, "NFC foreground dispatch enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling NFC foreground dispatch: ${e.message}", e)
        }
    }
    
    private fun disableNFCForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(requireActivity())
    }
    
    private fun updateNFCStatus() {
        when {
            nfcAdapter == null -> {
                textNfcStatus.text = "NFC Not Supported"
                iconNfcStatus.setColorFilter(requireContext().getColor(android.R.color.holo_red_dark))
                buttonStartPairing.isEnabled = false
            }
            !nfcAdapter!!.isEnabled -> {
                textNfcStatus.text = "NFC Disabled"
                iconNfcStatus.setColorFilter(requireContext().getColor(android.R.color.holo_orange_dark))
                buttonStartPairing.isEnabled = true
            }
            else -> {
                textNfcStatus.text = "NFC Ready"
                iconNfcStatus.setColorFilter(requireContext().getColor(android.R.color.holo_green_dark))
                buttonStartPairing.isEnabled = true
            }
        }
    }
    
    fun handleNFCIntent(intent: Intent) {
        if (!isPairingActive) {
            Log.d(TAG, "NFC intent received but pairing is not active")
            Toast.makeText(requireContext(), "Please start NFC pairing first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val action = intent.action
        Log.d(TAG, "Handling NFC intent with action: $action")
        
        when (action) {
            NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_TAG_DISCOVERED -> {
                Log.d(TAG, "NFC device discovered - attempting to read contact data via HCE")
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                if (tag != null) {
                    readContactFromHCEDevice(tag)
                } else {
                    Log.w(TAG, "No NFC tag found in intent")
                }
            }
            else -> {
                Log.w(TAG, "Unhandled NFC action: $action")
            }
        }
    }
    
    private fun readContactFromHCEDevice(tag: Tag) {
        // Use coroutine for NFC communication to avoid blocking UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Attempting to read contact data from HCE device")
                val contact = NFCReader.readContactFromNFC(tag)
                
                withContext(Dispatchers.Main) {
                    if (contact != null) {
                        Log.d(TAG, "Successfully received contact: ${contact.name} - ${contact.deviceId}")
                        processReceivedContact(contact)
                    } else {
                        Log.w(TAG, "Failed to read contact data from device")
                        Toast.makeText(requireContext(), "Could not read contact data. Make sure the other device is in pairing mode.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from HCE device: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error during pairing: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun processReceivedContact(contact: TrustedContact) {
        try {
            // Store the contact temporarily and start voice recording
            pendingContact = contact
            
            Toast.makeText(
                requireContext(), 
                "📞 Contact received: ${contact.name}\nStarting voice authentication...", 
                Toast.LENGTH_LONG
            ).show()
            
            // Stop NFC pairing
            stopNFCPairing()
            
            // Start voice recording activity
            val intent = Intent(requireContext(), VoiceRecordingActivity::class.java).apply {
                putExtra(VoiceRecordingActivity.EXTRA_CONTACT_NAME, contact.name)
                putExtra(VoiceRecordingActivity.EXTRA_CONTACT_DEVICE_ID, contact.deviceId)
            }
            voiceRecordingLauncher.launch(intent)
            
            Log.d(TAG, "Started voice recording for contact: ${contact.name} - ${contact.deviceId}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recording: ${e.message}", e)
            Toast.makeText(requireContext(), "Error starting voice recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveFinalContact(contact: TrustedContact) {
        try {
            // Save to trusted contacts with voice signature
            val success = ContactManager.addTrustedContact(requireContext(), contact)
            
            if (success) {
                Toast.makeText(
                    requireContext(), 
                    "✅ Contact added successfully: ${contact.name}\n🎙️ Voice signature recorded", 
                    Toast.LENGTH_LONG
                ).show()
                
                Log.d(TAG, "Successfully saved contact with voice signature: ${contact.name} - ${contact.deviceId}")
            } else {
                Toast.makeText(
                    requireContext(), 
                    "Contact updated: ${contact.name}", 
                    Toast.LENGTH_LONG
                ).show()
                
                Log.d(TAG, "Updated existing contact: ${contact.name} - ${contact.deviceId}")
            }
            
            // Navigate back to Dashboard (trusted contacts page)
            findNavController().navigate(R.id.navigation_dashboard)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving final contact: ${e.message}", e)
            Toast.makeText(requireContext(), "Error saving contact data", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateNFCStatus()
        if (isPairingActive) {
            enableNFCForegroundDispatch()
        }
        
        // Check if we should auto-start pairing (when navigated from dashboard add button)
        val shouldAutoStartPairing = arguments?.getBoolean("auto_start_pairing", false) ?: false
        if (shouldAutoStartPairing && !isPairingActive) {
            Log.d(TAG, "Auto-starting NFC pairing from navigation")
            // Clear the argument to prevent repeated auto-start
            arguments?.putBoolean("auto_start_pairing", false)
            startNFCPairing()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (isPairingActive) {
            disableNFCForegroundDispatch()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (isPairingActive) {
            stopNFCPairing()
        }
        _binding = null
    }
} 