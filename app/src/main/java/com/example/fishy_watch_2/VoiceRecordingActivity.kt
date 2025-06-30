package com.example.fishy_watch_2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

class VoiceRecordingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceRecording"
        private const val RECORD_AUDIO_REQUEST_CODE = 1001
        private const val REQUIRED_PHRASE = "The quick brown fox jumps over the lazy dog."
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_CONTACT_DEVICE_ID = "contact_device_id"
        const val EXTRA_VOICE_FILE_PATH = "voice_file_path"
    }

    // UI Components
    private lateinit var textContactName: TextView
    private lateinit var textInstructions: TextView
    private lateinit var textPhrase: TextView
    private lateinit var buttonRecord: Button
    private lateinit var buttonPlayback: Button
    private lateinit var buttonConfirm: Button
    private lateinit var buttonRetry: Button
    private lateinit var waveformContainer: View
    private lateinit var progressWaveform: ProgressBar
    private lateinit var iconMicrophone: ImageView
    private lateinit var textStatus: TextView

    // Recording
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingFile: File? = null
    private var waveformHandler: Handler? = null
    private var waveformRunnable: Runnable? = null

    // Contact data
    private var contactName: String = ""
    private var contactDeviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recording)

        // Get contact data from intent
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: ""
        contactDeviceId = intent.getStringExtra(EXTRA_CONTACT_DEVICE_ID) ?: ""

        initializeViews()
        setupClickListeners()
        checkAudioPermission()
    }

    private fun initializeViews() {
        textContactName = findViewById(R.id.textContactName)
        textInstructions = findViewById(R.id.textInstructions)
        textPhrase = findViewById(R.id.textPhrase)
        buttonRecord = findViewById(R.id.buttonRecord)
        buttonPlayback = findViewById(R.id.buttonPlayback)
        buttonConfirm = findViewById(R.id.buttonConfirm)
        buttonRetry = findViewById(R.id.buttonRetry)
        waveformContainer = findViewById(R.id.waveformContainer)
        progressWaveform = findViewById(R.id.progressWaveform)
        iconMicrophone = findViewById(R.id.iconMicrophone)
        textStatus = findViewById(R.id.textStatus)

        // Set initial state
        textContactName.text = "Recording voice signature for: $contactName"
        textPhrase.text = "\"$REQUIRED_PHRASE\""
        
        buttonPlayback.visibility = View.GONE
        buttonConfirm.visibility = View.GONE
        buttonRetry.visibility = View.GONE
        waveformContainer.visibility = View.GONE

        textStatus.text = "Ready to record"
    }

    private fun setupClickListeners() {
        buttonRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        buttonPlayback.setOnClickListener {
            playRecording()
        }

        buttonConfirm.setOnClickListener {
            confirmRecording()
        }

        buttonRetry.setOnClickListener {
            retryRecording()
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Microphone permission required for voice authentication", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkAudioPermission()
            return
        }

        try {
            // Create recording file
            recordingFile = File(filesDir, "voice_${contactDeviceId}_${System.currentTimeMillis()}.3gp")
            
            // Set up MediaRecorder
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(recordingFile!!.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                
                prepare()
                start()
            }

            isRecording = true
            
            // Update UI
            buttonRecord.text = "🛑 Stop Recording"
            buttonRecord.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            textStatus.text = "🎙️ Recording... Please say the phrase clearly"
            
            // Show waveform animation
            showWaveformAnimation()
            
            // Hide other buttons
            buttonPlayback.visibility = View.GONE
            buttonConfirm.visibility = View.GONE
            buttonRetry.visibility = View.GONE

            Log.d(TAG, "Recording started to: ${recordingFile!!.absolutePath}")

        } catch (e: IOException) {
            Log.e(TAG, "Recording failed to start: ${e.message}", e)
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Stop waveform animation
            hideWaveformAnimation()

            // Update UI
            buttonRecord.text = "🎙️ Start Recording"
            buttonRecord.backgroundTintList = getColorStateList(R.color.blue_500)
            textStatus.text = "✅ Recording completed!"

            // Show playback and action buttons
            buttonPlayback.visibility = View.VISIBLE
            buttonConfirm.visibility = View.VISIBLE
            buttonRetry.visibility = View.VISIBLE

            Log.d(TAG, "Recording stopped successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWaveformAnimation() {
        waveformContainer.visibility = View.VISIBLE
        progressWaveform.visibility = View.VISIBLE
        iconMicrophone.setColorFilter(getColor(android.R.color.holo_red_dark))

        // Animate the waveform
        waveformHandler = Handler(Looper.getMainLooper())
        waveformRunnable = object : Runnable {
            private var progress = 0
            private var increasing = true

            override fun run() {
                // Simulate waveform by animating progress bar
                if (increasing) {
                    progress += 5
                    if (progress >= 100) {
                        increasing = false
                    }
                } else {
                    progress -= 5
                    if (progress <= 20) {
                        increasing = true
                    }
                }
                
                progressWaveform.progress = progress
                
                if (isRecording) {
                    waveformHandler?.postDelayed(this, 100)
                }
            }
        }
        waveformHandler?.post(waveformRunnable!!)
    }

    private fun hideWaveformAnimation() {
        waveformContainer.visibility = View.GONE
        iconMicrophone.setColorFilter(getColor(android.R.color.darker_gray))
        waveformHandler?.removeCallbacks(waveformRunnable!!)
        waveformHandler = null
        waveformRunnable = null
    }

    private fun playRecording() {
        // TODO: Implement audio playback
        Toast.makeText(this, "Playing recording... (playback not implemented yet)", Toast.LENGTH_SHORT).show()
    }

    private fun confirmRecording() {
        val voiceFilePath = recordingFile?.absolutePath
        if (voiceFilePath != null) {
            // Return result to ProfileFragment
            val resultIntent = Intent().apply {
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CONTACT_DEVICE_ID, contactDeviceId)
                putExtra(EXTRA_VOICE_FILE_PATH, voiceFilePath)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } else {
            Toast.makeText(this, "No recording found. Please record first.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retryRecording() {
        // Delete current recording file
        recordingFile?.delete()
        recordingFile = null

        // Reset UI
        buttonPlayback.visibility = View.GONE
        buttonConfirm.visibility = View.GONE
        buttonRetry.visibility = View.GONE
        textStatus.text = "Ready to record again"

        Log.d(TAG, "Ready for retry recording")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up recording if activity is destroyed
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recording on destroy: ${e.message}")
            }
        }
        
        hideWaveformAnimation()
        mediaRecorder = null
    }
} 