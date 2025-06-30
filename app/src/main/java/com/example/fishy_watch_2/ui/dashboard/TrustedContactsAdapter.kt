package com.example.fishy_watch_2.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fishy_watch_2.R
import com.example.fishy_watch_2.TrustedContact

class TrustedContactsAdapter : RecyclerView.Adapter<TrustedContactsAdapter.ContactViewHolder>() {
    
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
        
        fun bind(contact: TrustedContact) {
            textName.text = contact.name
            textStatus.text = "● offline"
            textDevice.text = "Device: ${contact.deviceId}"
            textExchanged.text = "✓ Exchanged ${contact.getDisplayTimestamp()}"
            textLastSeen.text = "🕐 Last seen ${contact.getLastSeenText()}"
        }
    }
} 