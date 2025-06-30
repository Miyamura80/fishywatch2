package com.example.fishy_watch_2.ui.profile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fishy_watch_2.ContactManager
import com.example.fishy_watch_2.R
import com.example.fishy_watch_2.TrustedContact
import com.example.fishy_watch_2.databinding.FragmentProfileBinding
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.*

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
        
        // Setup intent filters for NFC
        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("application/fishy.watch")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                Log.e(TAG, "Malformed MIME type", e)
            }
        }
        
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        
        intentFiltersArray = arrayOf(ndefFilter, techFilter, tagFilter)
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
        Toast.makeText(requireContext(), "Profile saved!", Toast.LENGTH_SHORT).show()
        
        Log.d(TAG, "Profile saved - User: $userName")
    }
    
    private fun toggleNFCPairing() {
        if (!isPairingActive) {
            startNFCPairing()
        } else {
            stopNFCPairing()
        }
    }
    
    private fun startNFCPairing() {
        if (nfcAdapter == null || !nfcAdapter!!.isEnabled) {
            Toast.makeText(requireContext(), "Please enable NFC to pair with other devices", Toast.LENGTH_LONG).show()
            return
        }
        
        if (userName.isEmpty()) {
            Toast.makeText(requireContext(), "Please save your profile first", Toast.LENGTH_SHORT).show()
            return
        }
        
        isPairingActive = true
        buttonStartPairing.text = "Stop Pairing"
        buttonStartPairing.backgroundTintList = 
            requireContext().getColorStateList(android.R.color.holo_red_dark)
        textPairingInstructions.visibility = View.VISIBLE
        
        // Enable NFC foreground dispatch
        enableNFCForegroundDispatch()
        
        Toast.makeText(requireContext(), "NFC pairing started. Hold devices together.", Toast.LENGTH_LONG).show()
        Log.d(TAG, "NFC pairing started")
    }
    
    private fun stopNFCPairing() {
        isPairingActive = false
        buttonStartPairing.text = "Start NFC Pairing"
        buttonStartPairing.backgroundTintList = 
            requireContext().getColorStateList(R.color.blue_500)
        textPairingInstructions.visibility = View.GONE
        
        // Disable NFC foreground dispatch
        disableNFCForegroundDispatch()
        
        Toast.makeText(requireContext(), "NFC pairing stopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "NFC pairing stopped")
    }
    
    private fun enableNFCForegroundDispatch() {
        try {
            nfcAdapter?.enableForegroundDispatch(
                requireActivity(),
                pendingIntent,
                intentFiltersArray,
                arrayOf(arrayOf("android.nfc.tech.Ndef", "android.nfc.tech.NdefFormatable"))
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
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                Log.d(TAG, "NDEF discovered - reading contact data")
                val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                if (messages != null) {
                    processNdefMessages(messages)
                } else {
                    Log.w(TAG, "No NDEF messages found in intent")
                }
            }
            NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_TAG_DISCOVERED -> {
                Log.d(TAG, "Tag/Tech discovered - attempting to read and write contact data")
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                if (tag != null) {
                    // First try to read any existing data
                    tryReadFromTag(tag)
                    // Then try to write our data
                    writeContactToNFC(tag)
                } else {
                    Log.w(TAG, "No NFC tag found in intent")
                }
            }
            else -> {
                Log.w(TAG, "Unhandled NFC action: $action")
            }
        }
    }
    
    private fun processNdefMessages(messages: Array<android.os.Parcelable>) {
        Log.d(TAG, "Processing ${messages.size} NDEF messages")
        try {
            for (message in messages) {
                val ndefMessage = message as NdefMessage
                Log.d(TAG, "Processing NDEF message with ${ndefMessage.records.size} records")
                
                for (record in ndefMessage.records) {
                    val recordType = String(record.type, Charset.forName("UTF-8"))
                    Log.d(TAG, "Processing record type: $recordType")
                    
                    if (recordType == "application/fishy.watch") {
                        val payload = String(record.payload, Charset.forName("UTF-8"))
                        Log.d(TAG, "Found fishy.watch record with payload: $payload")
                        processReceivedContact(payload)
                        return // Process only the first matching record
                    }
                }
            }
            Log.w(TAG, "No fishy.watch records found in NDEF messages")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing NDEF messages: ${e.message}", e)
            Toast.makeText(requireContext(), "Error reading contact data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun tryReadFromTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                if (ndefMessage != null) {
                    Log.d(TAG, "Found existing NDEF message on tag, processing...")
                    for (record in ndefMessage.records) {
                        val recordType = String(record.type, Charset.forName("UTF-8"))
                        Log.d(TAG, "Found record type on tag: $recordType")
                        
                        if (recordType == "application/fishy.watch") {
                            val payload = String(record.payload, Charset.forName("UTF-8"))
                            Log.d(TAG, "Found fishy.watch record on tag: $payload")
                            processReceivedContact(payload)
                            break
                        }
                    }
                }
                ndef.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not read from tag (this is normal): ${e.message}")
            // This is expected for blank tags or tags with different data
        }
    }
    
    private fun writeContactToNFC(tag: Tag) {
        try {
            val contactData = createContactData()
            Log.d(TAG, "Writing contact data: $contactData")
            
            val payload = contactData.toByteArray(Charset.forName("UTF-8"))
            val ndefRecord = NdefRecord.createMime("application/fishy.watch", payload)
            val ndefMessage = NdefMessage(ndefRecord)
            
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (ndef.isWritable && ndef.maxSize >= ndefMessage.toByteArray().size) {
                    ndef.writeNdefMessage(ndefMessage)
                    ndef.close()
                    
                    Toast.makeText(requireContext(), "Contact shared successfully!", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "Contact written to NFC tag successfully")
                } else {
                    Log.e(TAG, "NFC tag is not writable or too small")
                    Toast.makeText(requireContext(), "NFC tag cannot be written to", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "Tag does not support NDEF")
                Toast.makeText(requireContext(), "NFC tag does not support data exchange", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to NFC: ${e.message}", e)
            Toast.makeText(requireContext(), "Error sharing contact: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createContactData(): String {
        val contactJson = JSONObject().apply {
            put("name", userName)
            put("deviceId", deviceId)
            put("timestamp", System.currentTimeMillis())
            put("appVersion", "1.0")
        }
        return contactJson.toString()
    }
    
    private fun processReceivedContact(contactData: String) {
        try {
            val contactJson = JSONObject(contactData)
            val contactName = contactJson.getString("name")
            val contactDeviceId = contactJson.getString("deviceId")
            val contactTimestamp = contactJson.optLong("timestamp", System.currentTimeMillis())
            val contactAppVersion = contactJson.optString("appVersion", "1.0")
            
            // Create TrustedContact object
            val contact = TrustedContact(
                name = contactName,
                deviceId = contactDeviceId,
                timestamp = contactTimestamp,
                appVersion = contactAppVersion
            )
            
            // Save to trusted contacts
            val success = ContactManager.addTrustedContact(requireContext(), contact)
            
            if (success) {
                Toast.makeText(
                    requireContext(), 
                    "✅ Contact added: $contactName", 
                    Toast.LENGTH_LONG
                ).show()
                
                Log.d(TAG, "Successfully saved contact: $contactName - $contactDeviceId")
            } else {
                Toast.makeText(
                    requireContext(), 
                    "❌ Failed to save contact: $contactName", 
                    Toast.LENGTH_LONG
                ).show()
                
                Log.e(TAG, "Failed to save contact: $contactName - $contactDeviceId")
            }
            
            // Stop pairing after successful exchange
            stopNFCPairing()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing received contact: ${e.message}", e)
            Toast.makeText(requireContext(), "Invalid contact data received", Toast.LENGTH_SHORT).show()
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