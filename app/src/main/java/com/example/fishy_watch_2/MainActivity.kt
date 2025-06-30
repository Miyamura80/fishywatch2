package com.example.fishy_watch_2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.fishy_watch_2.PersistentNotificationService
import com.example.fishy_watch_2.databinding.ActivityMainBinding
import com.example.fishy_watch_2.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupPermissionLaunchers()
        requestPermissions()
        
        // Handle NFC intent if app was started via NFC
        handleNFCIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle NFC intents when app is already running
        handleNFCIntent(intent)
    }
    
    private fun handleNFCIntent(intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Handling intent with action: $action")
        
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED || action == NfcAdapter.ACTION_TECH_DISCOVERED || action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            Log.d(TAG, "NFC intent received, checking if profile fragment is active")
            
            // Get the current fragment
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            val currentDestination = navController.currentDestination?.id
            
            if (currentDestination == R.id.navigation_profile) {
                // Profile fragment is active, pass the NFC intent to it
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.get(0)
                
                if (currentFragment is ProfileFragment) {
                    Log.d(TAG, "Passing NFC intent to ProfileFragment")
                    currentFragment.handleNFCIntent(intent)
                } else {
                    Log.w(TAG, "Current fragment is not ProfileFragment: ${currentFragment?.javaClass?.simpleName}")
                }
            } else {
                // Navigate to profile fragment and show a message
                Log.d(TAG, "NFC detected, but not on profile page. Current destination: $currentDestination")
                Toast.makeText(this, "NFC detected! Go to Profile tab to pair devices.", Toast.LENGTH_LONG).show()
                
                // Navigate to profile tab
                navController.navigate(R.id.navigation_profile)
                
                // Store the intent to be handled once profile fragment is loaded
                // Note: This is a simplified approach. In a production app, you might want to store
                // the intent and handle it once the fragment is properly loaded
            }
        }
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_dashboard, R.id.navigation_key_exchange, 
                R.id.navigation_debug_tools, R.id.navigation_profile
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    private fun setupPermissionLaunchers() {
        // Overlay permission launcher
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (Settings.canDrawOverlays(this)) {
                // Overlay permission granted, now check notification permission
                Log.d(TAG, "Overlay permission granted")
                requestNotificationPermission()
            } else {
                // Handle overlay permission denied
                Log.w(TAG, "Overlay permission denied")
                Toast.makeText(this, "Overlay permission is required for deepfake alerts", Toast.LENGTH_LONG).show()
            }
        }

        // Notification permission launcher (Android 13+)
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Notification permission granted
                Log.d(TAG, "Notification permission granted")
                startPersistentProtection()
                Toast.makeText(this, "fishy.watch protection is now active!", Toast.LENGTH_SHORT).show()
            } else {
                // Handle notification permission denied
                Log.w(TAG, "Notification permission denied")
                Toast.makeText(this, "Notification permission recommended for alerts", Toast.LENGTH_LONG).show()
                // Still start protection even without notification permission
                startPersistentProtection()
            }
        }
    }

    private fun requestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            // Request overlay permission
            val uri = Uri.parse("package:$packageName")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
            overlayPermissionLauncher.launch(intent)
        } else {
            // Overlay permission already granted, check notification permission
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires runtime permission for notifications
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // For Android < 13, notification permission is granted automatically
            startPersistentProtection()
        }
    }

    private fun startPersistentProtection() {
        try {
            Log.d(TAG, "Starting persistent protection notification automatically")
            PersistentNotificationService.startProtection(this)
            Log.d(TAG, "Persistent protection started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting automatic persistent protection: ${e.message}", e)
        }
    }
}