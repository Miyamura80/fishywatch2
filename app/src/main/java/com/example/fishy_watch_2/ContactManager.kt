package com.example.fishy_watch_2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class TrustedContact(
    val name: String,
    val deviceId: String,
    val timestamp: Long,
    val appVersion: String = "1.0"
) {
    fun getDisplayTimestamp(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getLastSeenText(): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val diffMinutes = diffMs / (1000 * 60)
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24
        
        return when {
            diffMinutes < 60 -> "$diffMinutes minutes ago"
            diffHours < 24 -> "$diffHours hours ago"
            diffDays == 1L -> "1 day ago"
            else -> "$diffDays days ago"
        }
    }
}

object ContactManager {
    private const val TAG = "ContactManager"
    private const val PREFS_NAME = "fishy_watch_contacts"
    private const val KEY_CONTACTS = "trusted_contacts"
    
    fun addTrustedContact(context: Context, contact: TrustedContact): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingContacts = getTrustedContacts(context).toMutableList()
            
            // Check if contact already exists (by device ID)
            val existingIndex = existingContacts.indexOfFirst { it.deviceId == contact.deviceId }
            if (existingIndex >= 0) {
                // Update existing contact with new timestamp
                existingContacts[existingIndex] = contact.copy(timestamp = System.currentTimeMillis())
                Log.d(TAG, "Updated existing contact: ${contact.name}")
            } else {
                // Add new contact
                existingContacts.add(contact)
                Log.d(TAG, "Added new contact: ${contact.name}")
            }
            
            // Save back to preferences
            val contactsJson = JSONArray()
            for (c in existingContacts) {
                val contactObj = JSONObject().apply {
                    put("name", c.name)
                    put("deviceId", c.deviceId)
                    put("timestamp", c.timestamp)
                    put("appVersion", c.appVersion)
                }
                contactsJson.put(contactObj)
            }
            
            prefs.edit().putString(KEY_CONTACTS, contactsJson.toString()).apply()
            Log.d(TAG, "Saved ${existingContacts.size} contacts to storage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving contact: ${e.message}", e)
            false
        }
    }
    
    fun getTrustedContacts(context: Context): List<TrustedContact> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val contactsJsonString = prefs.getString(KEY_CONTACTS, "[]") ?: "[]"
            val contactsJson = JSONArray(contactsJsonString)
            
            val contacts = mutableListOf<TrustedContact>()
            for (i in 0 until contactsJson.length()) {
                val contactObj = contactsJson.getJSONObject(i)
                val contact = TrustedContact(
                    name = contactObj.getString("name"),
                    deviceId = contactObj.getString("deviceId"),
                    timestamp = contactObj.getLong("timestamp"),
                    appVersion = contactObj.optString("appVersion", "1.0")
                )
                contacts.add(contact)
            }
            
            Log.d(TAG, "Loaded ${contacts.size} contacts from storage")
            contacts.sortedByDescending { it.timestamp } // Most recent first
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contacts: ${e.message}", e)
            emptyList()
        }
    }
    
    fun removeTrustedContact(context: Context, deviceId: String): Boolean {
        return try {
            val existingContacts = getTrustedContacts(context).toMutableList()
            val removedCount = existingContacts.removeAll { it.deviceId == deviceId }
            
            if (removedCount > 0) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val contactsJson = JSONArray()
                for (contact in existingContacts) {
                    val contactObj = JSONObject().apply {
                        put("name", contact.name)
                        put("deviceId", contact.deviceId)
                        put("timestamp", contact.timestamp)
                        put("appVersion", contact.appVersion)
                    }
                    contactsJson.put(contactObj)
                }
                
                prefs.edit().putString(KEY_CONTACTS, contactsJson.toString()).apply()
                Log.d(TAG, "Removed contact with device ID: $deviceId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing contact: ${e.message}", e)
            false
        }
    }
    
    fun clearAllContacts(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CONTACTS, "[]").apply()
            Log.d(TAG, "Cleared all contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing contacts: ${e.message}", e)
        }
    }
} 