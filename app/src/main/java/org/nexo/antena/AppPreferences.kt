package org.nexo.antena

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var customPackages: String
        get() = prefs.getString(KEY_CUSTOM_PACKAGES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_PACKAGES, value.trim()).apply()

    fun isPackageMonitored(packageName: String): Boolean {
        val monitored = getMonitoredPackages()
        return monitored.contains(packageName)
    }

    fun setPackageMonitored(appId: String, enabled: Boolean) {
        prefs.edit().putBoolean("pkg_$appId", enabled).apply()
    }

    fun isAppChecked(appId: String, defaultVal: Boolean = false): Boolean {
        return prefs.getBoolean("pkg_$appId", defaultVal)
    }

    fun getMonitoredPackages(): Set<String> {
        val result = mutableSetOf<String>()

        // Mapeo de apps conocidas a sus package names posibles
        if (isAppChecked("bbva", true)) {
            result.addAll(listOf("com.bbva.netcash", "ar.com.bbva.mobile", "com.bbva.bbvacontigo"))
        }
        if (isAppChecked("mercadopago", true)) {
            result.addAll(listOf("com.mercadopago.wallet"))
        }
        if (isAppChecked("personalpay", true)) {
            result.addAll(listOf("com.personalpay.app", "ar.com.personalpay"))
        }
        if (isAppChecked("galicia", false)) {
            result.addAll(listOf("com.bancogalicia.mobile"))
        }
        if (isAppChecked("santander", false)) {
            result.addAll(listOf("ar.com.santander.rio.mbanking"))
        }
        if (isAppChecked("bna", false)) {
            result.addAll(listOf("ar.gob.bna.app"))
        }
        if (isAppChecked("brubank", false)) {
            result.addAll(listOf("com.brubank"))
        }
        if (isAppChecked("uala", false)) {
            result.addAll(listOf("com.uala"))
        }
        if (isAppChecked("cuentadni", false)) {
            result.addAll(listOf("ar.gov.bapro.cuentadni"))
        }
        if (isAppChecked("modo", false)) {
            result.addAll(listOf("com.playdigital.modo"))
        }

        // Paquetes adicionales personalizados introducidos por el usuario
        val custom = customPackages
        if (custom.isNotBlank()) {
            custom.split(",").forEach {
                val pkg = it.trim()
                if (pkg.isNotEmpty()) result.add(pkg)
            }
        }

        return result
    }

    companion object {
        private const val PREFS_NAME = "nexo_antena_prefs"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_CUSTOM_PACKAGES = "custom_packages"
    }
}
