package com.example.fishy_watch_2.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fishy_watch_2.R
import com.example.fishy_watch_2.TrustedContact

class TrustedContactsAdapter(
    private val onDeleteClick: (TrustedContact) -> Unit
) : RecyclerView.Adapter<TrustedContactsAdapter.ContactViewHolder>() {
    
    private var contacts: List<TrustedContact> = emptyList()
    
    fun updateContacts(newContacts: List<TrustedContact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trusted_contact, parent, false)
        return ContactViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }
    
    override fun getItemCount(): Int = contacts.size
    
    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textContactName)
        private val textStatus: TextView = itemView.findViewById(R.id.textContactStatus)
        private val textDevice: TextView = itemView.findViewById(R.id.textContactDevice)
        private val textExchanged: TextView = itemView.findViewById(R.id.textContactExchanged)
        private val textLastSeen: TextView = itemView.findViewById(R.id.textContactLastSeen)
        private val buttonDelete: TextView = itemView.findViewById(R.id.buttonDeleteContact)
        
        fun bind(contact: TrustedContact) {
            textName.text = contact.name
            
            // Hide online/offline status as requested
            textStatus.visibility = View.GONE
            
            textDevice.text = "Device: ${contact.deviceId}"
            textExchanged.text = "✓ Exchanged ${contact.getDisplayTimestamp()}"
            
            // Show voice signature status instead of last seen
            if (contact.voiceSignaturePath != null) {
                textLastSeen.text = "🎙️ Voice signature recorded"
            } else {
                textLastSeen.text = "⚠️ Voice signature missing"
            }
            
            // Handle delete button click
            buttonDelete.setOnClickListener {
                onDeleteClick(contact)
            }
        }
    }
} 