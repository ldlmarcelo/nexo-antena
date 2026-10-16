package org.nexo.antena

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NotificationRelayService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        mostrarNotificacionPersistente()
        Log.i(TAG, "NotificationRelayService iniciado correctamente.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return

        // 1. Filtro local estricto: verificar si la app está en la lista blanca
        if (!prefs.isPackageMonitored(packageName)) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val finalContent = when {
            bigText.isNotBlank() && bigText.length > text.length -> bigText
            text.isNotBlank() -> text
            else -> subText
        }

        // Si no hay contenido legible útil, descartamos
        if (title.isBlank() && finalContent.isBlank()) {
            return
        }

        val timestamp = sbn.postTime

        Log.d(TAG, "Capturada notificación de $packageName: [$title] $finalContent")

        // 2. Despachar de forma asíncrona a la URL configurada
        val targetUrl = prefs.webhookUrl
        val apiKey = prefs.apiKey

        if (targetUrl.isBlank()) {
            Log.w(TAG, "Webhook URL no configurada. Despacho omitido.")
            return
        }

        serviceScope.launch {
            despacharNotificacion(
                targetUrl = targetUrl,
                apiKey = apiKey,
                packageName = packageName,
                title = title,
                text = finalContent,
                timestamp = timestamp
            )
        }
    }

    private fun despacharNotificacion(
        targetUrl: String,
        apiKey: String,
        packageName: String,
        title: String,
        text: String,
        timestamp: Long
    ) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "NexoAntena/1.0.0 (Android; Sovereign-P2P)")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("X-Inbox-Key", apiKey)
                }
            }

            val payload = JSONObject().apply {
                put("package_name", packageName)
                put("title", title)
                put("text", text)
                put("timestamp", timestamp)
                val extrasObj = JSONObject().apply {
                    put("android.title", title)
                    put("android.text", text)
                }
                put("extras", extrasObj)
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Log.i(TAG, "Notificación despachada con éxito a $targetUrl (HTTP $responseCode)")
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Fallo al despachar notificación (HTTP $responseCode): $errorMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error de red al despachar notificación a $targetUrl: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Muestra una notificación persistente obligatoria en la barra de estado.
     * Esto garantiza el principio ético anti-spyware: el usuario y portador
     * del dispositivo siempre ven que la antena está escuchando.
     */
    private fun mostrarNotificacionPersistente() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NEXO Antena - Estado de Escucha",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado permanente de captura y reenvío soberano"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_antenna)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "NexoAntenaService"
        private const val CHANNEL_ID = "nexo_antena_status_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
