package org.upsuper.mqttwaker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.core.net.toUri
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()

        checkDeviceAdminPermission()
        checkOverlayPermission()
        startServiceIfEnabled()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // Request permission
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            Toast.makeText(
                this,
                "Please grant 'Display over other apps' permission for screen waking to work properly",
                Toast.LENGTH_LONG
            ).show()
            startActivity(intent)
        }
    }

    private fun startServiceIfEnabled() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isServiceEnabled = prefs.getBoolean("service_enabled", false)

        if (isServiceEnabled) {
            val serviceIntent = Intent(this, MQTTService::class.java)
            startService(serviceIntent)
        }
    }

    private fun checkDeviceAdminPermission() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = ComponentName(this, MQTTWakerDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(adminComponentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "MQTTWaker needs device admin permissions to lock your screen when requested via MQTT.")
            startActivity(intent)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val selectCertLauncher = registerForActivityResult(
            object : ActivityResultContracts.OpenDocument() {
                override fun createIntent(context: Context, input: Array<String>): Intent {
                    val intent = super.createIntent(context, input)
                    // Add flags for persistent permissions
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    return intent
                }
            }
        ) { uri: Uri? ->
            uri?.let { selectedUri ->
                // Save the certificate URI in preferences
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                prefs.edit {
                    putString("mqtt_ssl_cert_uri", selectedUri.toString())
                }

                try {
                    // Request persistent permission to read this file
                    requireContext().contentResolver.takePersistableUriPermission(
                        selectedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Log.d("MainActivity", "Persistent permission granted for URI: $selectedUri")

                    // Update the summary to show the selected file name
                    updateCertificateSummary(selectedUri)

                    // Restart service if it's running to apply the new certificate
                    restartServiceIfRunning()
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Failed to take persistable permission", e)
                    Toast.makeText(requireContext(),
                        "Could not access certificate file persistently: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Service enable/disable handler
            findPreference<SwitchPreferenceCompat>("service_enabled")?.setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                val serviceIntent = Intent(requireContext(), MQTTService::class.java)

                if (isEnabled) {
                    requireContext().startService(serviceIntent)
                } else {
                    requireContext().stopService(serviceIntent)
                }
                true
            }

            // Certificate selection handler
            findPreference<Preference>("mqtt_ssl_select_cert")?.setOnPreferenceClickListener {
                selectCertLauncher.launch(arrayOf("*/*"))
                true
            }

            // Initialize certificate summary if we already have one
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val certUriString = prefs.getString("mqtt_ssl_cert_uri", null)
            if (certUriString != null) {
                try {
                    val uri = certUriString.toUri()
                    // Check if we can still access the file before updating summary
                    requireContext().contentResolver.persistedUriPermissions.find {
                        it.uri == uri && it.isReadPermission
                    }?.let {
                        // We have the permission for this URI, safe to update the summary
                        updateCertificateSummary(uri)
                    } ?: run {
                        // Permission not available, show warning and reset the pref
                        findPreference<Preference>("mqtt_ssl_select_cert")?.summary =
                            "Permission lost for previously selected file. Please select again."
                        // Clear the saved URI as we lost access
                        prefs.edit { remove("mqtt_ssl_cert_uri") }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error checking saved certificate URI", e)
                    // Clear the invalid URI
                    prefs.edit { remove("mqtt_ssl_cert_uri") }
                }
            }

            // Option to use custom certificate listener
            findPreference<SwitchPreferenceCompat>("mqtt_ssl_use_custom_cert")?.setOnPreferenceChangeListener { _, _ ->
                // Restart service if it's running to apply the certificate settings change
                restartServiceIfRunning()
                true
            }
        }

        private fun updateCertificateSummary(uri: Uri) {
            try {
                val fileName = getFileNameFromUri(requireContext(), uri) ?: uri.lastPathSegment ?: "Selected"
                findPreference<Preference>("mqtt_ssl_select_cert")?.summary = "Selected: $fileName"
            } catch (e: SecurityException) {
                Log.e("MainActivity", "Security exception accessing certificate URI", e)
                findPreference<Preference>("mqtt_ssl_select_cert")?.summary = "Error accessing selected file"
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting file name", e)
                findPreference<Preference>("mqtt_ssl_select_cert")?.summary = "Selected file"
            }
        }

        private fun getFileNameFromUri(context: Context, uri: Uri): String? {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            return it.getString(displayNameIndex)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MainActivity", "Security exception querying URI", e)
                throw e
            } catch (e: Exception) {
                Log.e("MainActivity", "Error querying URI", e)
            }
            return null
        }

        private fun restartServiceIfRunning() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val isServiceEnabled = prefs.getBoolean("service_enabled", false)

            if (isServiceEnabled) {
                val serviceIntent = Intent(requireContext(), MQTTService::class.java)
                requireContext().stopService(serviceIntent)
                requireContext().startService(serviceIntent)
            }
        }
    }
}
