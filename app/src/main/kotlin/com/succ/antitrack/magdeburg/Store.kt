package com.succ.antitrack.magdeburg

import android.content.Context
import java.util.UUID

class Store(context: Context) {
    private val app = context.applicationContext
    private val p = app.getSharedPreferences("antitrack", Context.MODE_PRIVATE)

    fun deviceLocked(): Boolean =
        app.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked == true

    init {
        if (p.contains("deviceId")) p.edit().remove("deviceId").apply()
    }

    private fun sealed(key: String): String? = p.getString(key, null)?.let(TokenCipher::decrypt)
    private fun seal(key: String, v: String?) =
        p.edit().putString(key, v?.let(TokenCipher::encrypt)).apply()

    var accessToken: String?
        get() = sealed("access.enc")
        set(v) = seal("access.enc", v)

    var refreshToken: String?
        get() = sealed("refresh.enc")
        set(v) = seal("refresh.enc", v)

    var pinnedDeviceId: String?
        get() = p.getString("pinnedDeviceId", null)
        set(v) = p.edit().putString("pinnedDeviceId", v?.trim()?.takeIf { it.isNotEmpty() }).apply()

    fun requestDeviceId(): String = pinnedDeviceId ?: UUID.randomUUID().toString()

    var autoOpenTicket: Boolean
        get() = p.getBoolean("autoOpenTicket", false)
        set(v) = p.edit().putBoolean("autoOpenTicket", v).apply()

    var showOnLockscreen: Boolean
        get() = p.getBoolean("showOnLockscreen", false)
        set(v) = p.edit().putBoolean("showOnLockscreen", v).apply()

    var lastTicket: Pair<Long, String>?
        get() = p.getLong("lastOrderId", -1).takeIf { it >= 0 }?.let { it to (p.getString("lastOrderName", null) ?: "Ticket") }
        set(v) = p.edit().putLong("lastOrderId", v?.first ?: -1).putString("lastOrderName", v?.second).apply()

    var manifestoSeen: Boolean
        get() = p.getBoolean("manifestoSeen", false)
        set(v) = p.edit().putBoolean("manifestoSeen", v).apply()

    val loggedIn: Boolean get() = p.contains("refresh.enc") || p.contains("access.enc")

    fun saveTokens(access: String?, refresh: String?) {
        p.edit()
            .putString("access.enc", access?.let(TokenCipher::encrypt))
            .putString("refresh.enc", refresh?.let(TokenCipher::encrypt))
            .apply()
    }

    fun logout() {
        p.edit().remove("access.enc").remove("refresh.enc").remove("lastOrderId").remove("lastOrderName").apply()
        TokenCipher.destroy()
    }
}
