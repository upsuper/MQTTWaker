package org.upsuper.mqttwaker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.QoS
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import androidx.core.net.toUri

class MQTTService : Service() {
    companion object {
        private const val TAG = "MQTTService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "mqtt_waker_channel"
    }

    private lateinit var mqttClient: MqttAndroidClient
    private var isConnected = false
    private var serverUri = ""
    private var topic = ""
    private var clientId = ""
    private var username = ""
    private var password = ""
    private var browserUrl = ""
    private var useCustomCert = false
    private var customCertUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("MQTT Waker Service Starting"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadSettings()
        connectToMqttBroker()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnectMqtt()
        super.onDestroy()
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        serverUri = prefs.getString("mqtt_server", "") ?: ""
        topic = prefs.getString("mqtt_topic", "") ?: ""
        username = prefs.getString("mqtt_username", "") ?: ""
        password = prefs.getString("mqtt_password", "") ?: ""
        clientId = prefs.getString("mqtt_client_id", "") ?: ""
        browserUrl = prefs.getString("browser_url", "") ?: ""
        useCustomCert = prefs.getBoolean("mqtt_ssl_use_custom_cert", false)
        customCertUri = prefs.getString("mqtt_ssl_cert_uri", null)?.toUri()

        if (clientId.isBlank()) {
            clientId = "MQTTWaker_" + UUID.randomUUID().toString()
        }

        Log.d(
            TAG,
            "Settings loaded - Server: $serverUri, Topic: $topic, Username: ${if (username.isNotBlank()) "set" else "not set"}, SSL: ${if (useCustomCert) "custom cert" else "system certs"}"
        )
    }

    private fun connectToMqttBroker() {
        if (serverUri.isBlank() || topic.isBlank()) {
            Log.e(TAG, "MQTT server or topic not configured")
            updateNotification("Error: MQTT server or topic not configured")
            return
        }

        mqttClient = MqttAndroidClient(applicationContext, serverUri, clientId)
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                isConnected = true
                Log.d(TAG, "Connected to MQTT broker: $serverURI")
                updateNotification("Connected to MQTT broker")
                subscribeToTopic()
            }

            override fun connectionLost(cause: Throwable?) {
                isConnected = false
                Log.e(TAG, "Connection lost to MQTT broker", cause)
                updateNotification("Connection lost to MQTT broker")
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                // This is not called since we use a message listener when subscribing
            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {
                // Not used for subscription-only client
            }
        })

        val connectOptions = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false

            if (username.isNotBlank()) {
                userName = username
                password = this@MQTTService.password.toCharArray()
            }

            // Configure SSL if the serverUri starts with ssl://
            if (serverUri.startsWith("ssl://")) {
                try {
                    // Create SSL socket factory
                    val sslContext = if (useCustomCert && customCertUri != null) {
                        try {
                            createCustomSSLContext(customCertUri!!)
                        } catch (se: SecurityException) {
                            Log.e(TAG, "Security exception accessing certificate", se)
                            updateNotification("Cannot access certificate: permission denied")
                            // Fall back to system certificates
                            SSLContext.getInstance("TLS")
                        } catch (ce: Exception) {
                            Log.e(TAG, "Error loading custom certificate", ce)
                            updateNotification("Certificate error: ${ce.message}")
                            // Fall back to system certificates
                            SSLContext.getInstance("TLS")
                        }
                    } else {
                        SSLContext.getInstance("TLS")
                    }

                    socketFactory = sslContext.socketFactory
                    Log.d(TAG, "SSL configured for MQTT connection")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure SSL", e)
                    updateNotification("Failed to configure SSL: ${e.message}")
                }
            }
        }

        try {
            mqttClient.connect(connectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    val disconnectedBufferOptions = DisconnectedBufferOptions().apply {
                        isBufferEnabled = true
                        bufferSize = 100
                        isPersistBuffer = false
                        isDeleteOldestMessages = false
                    }
                    mqttClient.setBufferOpts(disconnectedBufferOptions)
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.e(TAG, "Failed to connect to MQTT broker", exception)
                    updateNotification("Failed to connect to MQTT broker")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to MQTT broker", e)
            updateNotification("Error connecting to MQTT broker: ${e.message}")
        }
    }

    private fun subscribeToTopic() {
        try {
            mqttClient.subscribe(topic, QoS.AtMostOnce.value) { topic, message ->
                val payload = String(message.payload)
                Log.d(TAG, "Message received on topic $topic: $payload")

                when (payload.trim().lowercase()) {
                    "on" -> wakeScreen()
                    "off" -> lockScreen()
                    else -> Log.d(TAG, "Unknown command: $payload")
                }
            }

            updateNotification("Subscribed to topic: $topic")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to topic", e)
            updateNotification("Failed to subscribe to topic: ${e.message}")
        }
    }

    private fun wakeScreen() {
        Log.d(TAG, "Wake screen command received")

        try {
            // Show a notification that we're waking the screen
            updateNotification("Waking screen")

            // Start the ScreenWakeService which will handle drawing over other apps
            // and then launching the WakeActivity
            val intent = Intent(this, ScreenWakeService::class.java)
            startService(intent)
            Log.d(TAG, "ScreenWakeService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen", e)
            updateNotification("Failed to wake screen: ${e.message}")
        }
    }

    private fun lockScreen() {
        Log.d(TAG, "Lock screen command received")

        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = ComponentName(this, MQTTWakerDeviceAdminReceiver::class.java)

        if (devicePolicyManager.isAdminActive(adminComponentName)) {
            // We have admin privileges, we can lock the device
            try {
                devicePolicyManager.lockNow()
                Log.d(TAG, "Device locked")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lock screen", e)
                updateNotification("Failed to lock screen: ${e.message}")
            }
        } else {
            // We don't have admin privileges, show a notification
            Log.d(TAG, "No device admin permission, going to settings")
            updateNotification("Device admin permission needed for screen locking")

            // Create intent to go to admin settings
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "MQTTWaker needs device admin permissions to lock your screen when requested via MQTT.")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun disconnectMqtt() {
        try {
            if (::mqttClient.isInitialized && isConnected) {
                mqttClient.disconnect()
                Log.d(TAG, "Disconnected from MQTT broker")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from MQTT broker", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MQTT Waker Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "MQTT Waker Service notifications"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Waker")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createCustomSSLContext(certUri: Uri): SSLContext {
        // Check if we have permission to access this URI
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == certUri && it.isReadPermission
        }

        if (!hasPermission) {
            throw SecurityException("No permission to access certificate file")
        }

        val certificateFactory = CertificateFactory.getInstance("X.509")
        val inputStream = contentResolver.openInputStream(certUri)
            ?: throw IllegalArgumentException("Cannot read certificate file")

        val certificate = inputStream.use { stream ->
            certificateFactory.generateCertificate(stream) as X509Certificate
        }

        // Create a KeyStore containing our certificate
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("ca", certificate)

        // Create a TrustManager that trusts the certificate in our KeyStore
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        // Create an SSLContext that uses our TrustManager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagerFactory.trustManagers, null)

        Log.d(TAG, "Custom SSL context created with certificate: ${certificate.subjectX500Principal}")
        return sslContext
    }
}
