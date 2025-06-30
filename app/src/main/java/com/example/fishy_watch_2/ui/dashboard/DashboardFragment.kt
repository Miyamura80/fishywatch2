package com.example.fishy_watch_2.ui.dashboard

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fishy_watch_2.ContactManager
import com.example.fishy_watch_2.R
import com.example.fishy_watch_2.TrustedContact
import com.example.fishy_watch_2.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    companion object {
        private const val TAG = "DashboardFragment"
    }

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    // UI components
    private lateinit var switchCloudMode: Switch
    private lateinit var iconLocal: ImageView
    private lateinit var iconCloud: ImageView
    private lateinit var buttonAddContact: ImageView
    private lateinit var recyclerTrustedContacts: RecyclerView
    private lateinit var textNoContacts: TextView
    private lateinit var contactsAdapter: TrustedContactsAdapter
    
    // State
    private var isCloudMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initializeViews()
        setupToggleSwitch()
        setupAddContactButton()
        setupContactsList()
        loadTrustedContacts()

        return root
    }

    private fun initializeViews() {
        switchCloudMode = binding.root.findViewById(R.id.switchCloudMode)
        iconLocal = binding.root.findViewById(R.id.iconLocal)
        iconCloud = binding.root.findViewById(R.id.iconCloud)
        buttonAddContact = binding.root.findViewById(R.id.buttonAddContact)
        recyclerTrustedContacts = binding.root.findViewById(R.id.recyclerTrustedContacts)
        textNoContacts = binding.root.findViewById(R.id.textNoContacts)
    }

    private fun setupToggleSwitch() {
        // Set initial state (local mode)
        updateToggleState(false)
        
        switchCloudMode.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Toggle switched to: ${if (isChecked) "Cloud" else "Local"} mode")
            updateToggleState(isChecked)
            
            val mode = if (isChecked) "Cloud" else "Local"
            Toast.makeText(requireContext(), "Switched to $mode mode", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupAddContactButton() {
        buttonAddContact.setOnClickListener {
            Log.d(TAG, "Add contact button clicked")
            Toast.makeText(requireContext(), "Opening NFC pairing...", Toast.LENGTH_SHORT).show()
            
            // Navigate to profile page and signal to start NFC pairing automatically
            val bundle = bundleOf("auto_start_pairing" to true)
            findNavController().navigate(R.id.navigation_profile, bundle)
        }
    }
    
    private fun setupContactsList() {
        contactsAdapter = TrustedContactsAdapter { contact ->
            showDeleteConfirmationDialog(contact)
        }
        recyclerTrustedContacts.layoutManager = LinearLayoutManager(requireContext())
        recyclerTrustedContacts.adapter = contactsAdapter
    }
    
    private fun showDeleteConfirmationDialog(contact: TrustedContact) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Trusted Contact")
            .setMessage("Are you sure you want to delete '${contact.name}'?\n\nThis will remove their contact information and voice signature from your trusted contacts.")
            .setPositiveButton("Delete") { _, _ ->
                deleteContact(contact)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    
    private fun deleteContact(contact: TrustedContact) {
        val success = ContactManager.removeTrustedContact(requireContext(), contact.deviceId)
        
        if (success) {
            Toast.makeText(requireContext(), "✅ Deleted '${contact.name}' from trusted contacts", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Successfully deleted contact: ${contact.name} - ${contact.deviceId}")
            loadTrustedContacts() // Refresh the list
        } else {
            Toast.makeText(requireContext(), "❌ Failed to delete contact", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to delete contact: ${contact.name} - ${contact.deviceId}")
        }
    }
    
    private fun loadTrustedContacts() {
        val contacts = ContactManager.getTrustedContacts(requireContext())
        Log.d(TAG, "Loaded ${contacts.size} trusted contacts")
        
        if (contacts.isEmpty()) {
            recyclerTrustedContacts.visibility = View.GONE
            textNoContacts.visibility = View.VISIBLE
        } else {
            recyclerTrustedContacts.visibility = View.VISIBLE
            textNoContacts.visibility = View.GONE
            contactsAdapter.updateContacts(contacts)
        }
    }

    private fun updateToggleState(cloudMode: Boolean) {
        isCloudMode = cloudMode
        
        if (cloudMode) {
            // Cloud mode - cloud icon active (blue), local icon inactive (gray)
            iconCloud.setColorFilter(ContextCompat.getColor(requireContext(), R.color.blue_500))
            iconLocal.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        } else {
            // Local mode - local icon active (blue), cloud icon inactive (gray)
            iconLocal.setColorFilter(ContextCompat.getColor(requireContext(), R.color.blue_500))
            iconCloud.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        }
        
        Log.d(TAG, "UI updated for ${if (cloudMode) "Cloud" else "Local"} mode")
    }

    fun isInCloudMode(): Boolean {
        return isCloudMode
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh contacts list in case new contacts were added
        loadTrustedContacts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}