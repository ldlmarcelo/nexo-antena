package org.nexo.antena

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.nexo.antena.databinding.ActivityMainBinding
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences

    // Conjunto en memoria de paquetes seleccionados
    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        initViews()
        cargarConfiguracion()
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadoPermiso()
    }

    private fun initViews() {
        binding.btnGrantPermission.setOnClickListener {
            abrirAjustesNotificaciones()
        }

        binding.switchForwardAll.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutFilterContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        binding.btnSelectApps.setOnClickListener {
            mostrarDialogoSeleccionApps()
        }

        binding.btnSave.setOnClickListener {
            guardarConfiguracion()
        }

        binding.btnTest.setOnClickListener {
            probarConexion()
        }
    }

    private fun actualizarEstadoPermiso() {
        val habilitado = isNotificationServiceEnabled()
        if (habilitado) {
            binding.tvPermissionStatus.text = getString(R.string.status_permission_granted)
            binding.tvPermissionStatus.setTextColor(getColor(R.color.status_green))
            binding.btnGrantPermission.text = "Ajustes de Notificación"
        } else {
            binding.tvPermissionStatus.text = getString(R.string.status_permission_missing)
            binding.tvPermissionStatus.setTextColor(getColor(R.color.status_amber))
            binding.btnGrantPermission.text = getString(R.string.btn_grant_permission)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packages.contains(packageName)
    }

    private fun abrirAjustesNotificaciones() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir ajustes: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarConfiguracion() {
        binding.etWebhookUrl.setText(prefs.webhookUrl)
        binding.etApiKey.setText(prefs.apiKey)

        val forwardAll = prefs.forwardAll
        binding.switchForwardAll.isChecked = forwardAll
        binding.layoutFilterContainer.visibility = if (forwardAll) View.GONE else View.VISIBLE

        selectedPackages.clear()
        selectedPackages.addAll(prefs.getMonitoredPackages())

        actualizarResumenAppsSeleccionadas()
    }

    private fun actualizarResumenAppsSeleccionadas() {
        val count = selectedPackages.size
        if (count == 0) {
            binding.tvSelectedAppsSummary.text = "Ninguna aplicación seleccionada"
        } else {
            binding.tvSelectedAppsSummary.text = "$count aplicaciones seleccionadas para reenvío"
        }
    }

    private data class AppItem(val label: String, val packageName: String)

    private fun mostrarDialogoSeleccionApps() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Filtramos apps visibles que tengan intent de lanzamiento o sean relevantes
        val appItems = installedApps
            .filter { app ->
                // Omitir esta misma app
                app.packageName != packageName &&
                (pm.getLaunchIntentForPackage(app.packageName) != null ||
                 (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
            }
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                AppItem(label = label, packageName = app.packageName)
            }
            .sortedBy { it.label.lowercase() }

        if (appItems.isEmpty()) {
            Toast.makeText(this, "No se detectaron aplicaciones instaladas", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = appItems.map { "${it.label} (${it.packageName})" }.toTypedArray()
        val checkedItems = appItems.map { selectedPackages.contains(it.packageName) }.toBooleanArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Seleccionar Aplicaciones")
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                val pkg = appItems[which].packageName
                if (isChecked) {
                    selectedPackages.add(pkg)
                } else {
                    selectedPackages.remove(pkg)
                }
            }
            .setPositiveButton("Listo") { _, _ ->
                actualizarResumenAppsSeleccionadas()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarConfiguracion() {
        prefs.webhookUrl = binding.etWebhookUrl.text?.toString() ?: ""
        prefs.apiKey = binding.etApiKey.text?.toString() ?: ""
        prefs.forwardAll = binding.switchForwardAll.isChecked

        // Incorporar los paquetes manuales si fueron ingresados
        val manual = binding.etCustomPackages.text?.toString() ?: ""
        if (manual.isNotBlank()) {
            manual.split(",").forEach {
                val pkg = it.trim()
                if (pkg.isNotEmpty()) selectedPackages.add(pkg)
            }
            binding.etCustomPackages.setText("")
        }

        prefs.setMonitoredPackages(selectedPackages)
        actualizarResumenAppsSeleccionadas()

        Toast.makeText(this, "Configuración guardada correctamente", Toast.LENGTH_SHORT).show()
    }

    private fun probarConexion() {
        val targetUrl = binding.etWebhookUrl.text?.toString()?.trim() ?: ""
        val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""

        if (targetUrl.isBlank()) {
            Toast.makeText(this, "Ingresa primero la URL del Webhook", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvTestResult.visibility = View.VISIBLE
        binding.tvTestResult.text = "Probando conexión con $targetUrl..."
        binding.tvTestResult.setTextColor(getColor(R.color.text_secondary))

        lifecycleScope.launch(Dispatchers.IO) {
            val result = ejecutarPing(targetUrl, apiKey)
            withContext(Dispatchers.Main) {
                binding.tvTestResult.visibility = View.VISIBLE
                if (result.exito) {
                    binding.tvTestResult.text = "✅ Conexión exitosa (HTTP ${result.codigo}): ${result.mensaje}"
                    binding.tvTestResult.setTextColor(getColor(R.color.status_green))
                } else {
                    binding.tvTestResult.text = "❌ Error (${result.codigo}): ${result.mensaje}"
                    binding.tvTestResult.setTextColor(Color.RED)
                }
            }
        }
    }

    private data class PingResult(val exito: Boolean, val codigo: Int, val mensaje: String)

    private fun ejecutarPing(targetUrl: String, apiKey: String): PingResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "NexoAntena/1.0.0 (Android; Sovereign-P2P)")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("X-Inbox-Key", apiKey)
                }
            }

            val testPayload = JSONObject().apply {
                put("package_name", "org.nexo.antena")
                put("title", "Ping de Prueba")
                put("text", "Verificación de antena soberana P2P")
                put("timestamp", System.currentTimeMillis())
                put("extras", JSONObject().apply { put("test", true) })
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(testPayload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Sin cuerpo de error"
            }

            PingResult(code in 200..299, code, responseBody.take(120))
        } catch (e: Exception) {
            PingResult(false, 0, e.message ?: "Error desconocido")
        } finally {
            connection?.disconnect()
        }
    }
}
