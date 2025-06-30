package com.example.fishy_watch_2

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset

class NFCReader {
    
    companion object {
        private const val TAG = "NFCReader"
        
        // AID for fishy.watch app
        private const val FISHY_WATCH_AID = "F0394148148100"
        
        // APDU Commands
        private val SELECT_APDU = buildSelectApdu()
        private val GET_DATA_APDU = byteArrayOf(0x00.toByte(), 0xCA.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte())
        
        private fun buildSelectApdu(): ByteArray {
            val aidBytes = FISHY_WATCH_AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), aidBytes.size.toByte()) + aidBytes + byteArrayOf(0x00.toByte())
        }
        
        fun readContactFromNFC(tag: Tag): TrustedContact? {
            val isoDep = IsoDep.get(tag) ?: run {
                Log.w(TAG, "Tag does not support ISO-DEP")
                return null
            }
            
            try {
                isoDep.connect()
                Log.d(TAG, "Connected to NFC tag/device")
                
                // Select the fishy.watch application
                Log.d(TAG, "Sending SELECT command")
                val selectResponse = isoDep.transceive(SELECT_APDU)
                Log.d(TAG, "SELECT response: ${selectResponse.joinToString(" ") { "%02X".format(it) }}")
                
                if (!isSuccessResponse(selectResponse)) {
                    Log.w(TAG, "SELECT command failed")
                    return null
                }
                
                // Get contact data
                Log.d(TAG, "Sending GET DATA command")
                val dataResponse = isoDep.transceive(GET_DATA_APDU)
                Log.d(TAG, "GET DATA response: ${dataResponse.joinToString(" ") { "%02X".format(it) }}")
                
                return parseContactData(dataResponse)
                
            } catch (e: IOException) {
                Log.e(TAG, "NFC communication error: ${e.message}", e)
                return null
            } finally {
                try {
                    isoDep.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Error closing NFC connection: ${e.message}")
                }
            }
        }
        
        private fun isSuccessResponse(response: ByteArray): Boolean {
            if (response.size < 2) return false
            return response[response.size - 2] == 0x90.toByte() && response[response.size - 1] == 0x00.toByte()
        }
        
        private fun parseContactData(response: ByteArray): TrustedContact? {
            if (response.size < 2) {
                Log.w(TAG, "Response too short")
                return null
            }
            
            // Check if response ends with success code (90 00)
            if (!isSuccessResponse(response)) {
                Log.w(TAG, "GET DATA command failed")
                return null
            }
            
            try {
                // Remove the last 2 bytes (status code) and parse JSON
                val dataBytes = response.sliceArray(0 until response.size - 2)
                val jsonString = String(dataBytes, Charset.forName("UTF-8"))
                Log.d(TAG, "Received contact data: $jsonString")
                
                val jsonObject = JSONObject(jsonString)
                val name = jsonObject.getString("name")
                val deviceId = jsonObject.getString("deviceId")
                val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
                
                return TrustedContact(name, deviceId, timestamp)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing contact data: ${e.message}", e)
                return null
            }
        }
    }
} 