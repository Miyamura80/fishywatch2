package com.example.fishy_watch_2

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import org.json.JSONObject
import java.nio.charset.Charset

class NFCCardEmulationService : HostApduService() {
    
    companion object {
        private const val TAG = "NFCCardEmulation"
        
        // AID (Application ID) for our fishy.watch app
        private const val FISHY_WATCH_AID = "F0394148148100"
        
        // APDU Commands
        private val SELECT_APDU_HEADER = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte())
        private val GET_DATA_APDU_HEADER = byteArrayOf(0x00.toByte(), 0xCA.toByte(), 0x01.toByte(), 0x00.toByte())
        
        // Response codes
        private val SUCCESS_SW = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val FAILURE_SW = byteArrayOf(0x6F.toByte(), 0x00.toByte())
        private val NOT_FOUND_SW = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        
        // Static contact data (will be set when pairing starts)
        var contactData: String? = null
    }
    
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            Log.w(TAG, "Received null APDU")
            return FAILURE_SW
        }
        
        Log.d(TAG, "Received APDU: ${commandApdu.joinToString(" ") { "%02X".format(it) }}")
        
        return when {
            isSelectAidApdu(commandApdu) -> {
                Log.d(TAG, "SELECT AID command received")
                SUCCESS_SW
            }
            isGetDataApdu(commandApdu) -> {
                Log.d(TAG, "GET DATA command received")
                handleGetDataCommand()
            }
            else -> {
                Log.w(TAG, "Unknown APDU command")
                NOT_FOUND_SW
            }
        }
    }
    
    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "NFC deactivated, reason: $reason")
    }
    
    private fun isSelectAidApdu(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_APDU_HEADER.size + 1) return false
        
        // Check APDU header
        for (i in SELECT_APDU_HEADER.indices) {
            if (apdu[i] != SELECT_APDU_HEADER[i]) return false
        }
        
        // Check AID length
        val aidLength = apdu[SELECT_APDU_HEADER.size].toInt() and 0xFF
        if (apdu.size < SELECT_APDU_HEADER.size + 1 + aidLength) return false
        
        // Extract and check AID
        val receivedAid = apdu.sliceArray(SELECT_APDU_HEADER.size + 1 until SELECT_APDU_HEADER.size + 1 + aidLength)
        val expectedAid = FISHY_WATCH_AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        return receivedAid.contentEquals(expectedAid)
    }
    
    private fun isGetDataApdu(apdu: ByteArray): Boolean {
        return apdu.size >= GET_DATA_APDU_HEADER.size &&
                apdu.sliceArray(0 until GET_DATA_APDU_HEADER.size).contentEquals(GET_DATA_APDU_HEADER)
    }
    
    private fun handleGetDataCommand(): ByteArray {
        val data = contactData
        if (data == null) {
            Log.w(TAG, "No contact data available")
            return NOT_FOUND_SW
        }
        
        try {
            val dataBytes = data.toByteArray(Charset.forName("UTF-8"))
            Log.d(TAG, "Sending contact data: $data")
            
            // Return data + success code
            return dataBytes + SUCCESS_SW
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing contact data: ${e.message}", e)
            return FAILURE_SW
        }
    }
    
    fun setContactData(name: String, deviceId: String) {
        val contactJson = JSONObject().apply {
            put("name", name)
            put("deviceId", deviceId)
            put("timestamp", System.currentTimeMillis())
            put("appVersion", "1.0")
        }
        contactData = contactJson.toString()
        Log.d(TAG, "Contact data set: $contactData")
    }
} 