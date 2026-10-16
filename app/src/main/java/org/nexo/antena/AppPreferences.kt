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

    var forwardAll: Boolean
        get() = prefs.getBoolean(KEY_FORWARD_ALL, false)
        set(value) = prefs.edit().putBoolean(KEY_FORWARD_ALL, value).apply()

    fun getMonitoredPackages(): Set<String> {
        return prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setMonitoredPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_MONITORED_PACKAGES, packages).apply()
    }

    fun isPackageMonitored(packageName: String): Boolean {
        if (forwardAll) return true
        val monitored = getMonitoredPackages()
        return monitored.contains(packageName)
    }

    companion object {
        private const val PREFS_NAME = "nexo_antena_prefs"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_FORWARD_ALL = "forward_all"
        private const val KEY_MONITORED_PACKAGES = "monitored_packages"
    }
}
