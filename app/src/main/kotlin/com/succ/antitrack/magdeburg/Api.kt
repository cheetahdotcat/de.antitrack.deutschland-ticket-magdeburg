package com.succ.antitrack.magdeburg

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(message: String, val loggedOut: Boolean = false) : Exception(message)

data class Subscription(
    val orderId: Long,
    val name: String,
    val code: String?,
    val validFrom: String?,
    val validity: String?,
)

data class Ticket(
    val ticketId: String?,
    val productName: String?,
    val price: String?,
    val gueltigBis: String?,
    val zeitlicheGueltigkeit: String?,
    val raeumlicheGueltigkeit: String?,
    val tarif: String?,
    val html: String?,
    val pdf: ByteArray?,
    val holderName: String?,
    val holderBirth: String?,

    val status: String?,
)

class MvbApi(private val store: Store) {
    companion object {
        const val BASE = "https://prod.tafmobile.de"

        private val CLIENT_ID = BuildConfig.MVB_CLIENT_ID
        private val CLIENT_SECRET = BuildConfig.MVB_CLIENT_SECRET
        const val USER_AGENT = "Mobility Android 1.0.0 MvbApp 1.0.5 Java/17"
        private const val REFRESH_SKEW_S = 120
    }

    suspend fun subscriptions(): List<Subscription> = withContext(Dispatchers.IO) {
        val o = get("/restapi/order/v1?onlySubscription=true", store.requestDeviceId())
        val arr = o.optJSONArray("ordersResult") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val order = arr.getJSONObject(i)
            val sub = order.optJSONObject("subscription")
            Subscription(
                orderId = order.getLong("id"),
                name = sub?.str("name") ?: "Bestellung ${order.getLong("id")}",
                code = sub?.str("subscriptionCode"),
                validFrom = sub?.str("validFrom"),
                validity = sub?.str("subscriptionValidity"),
            )
        }
    }

    suspend fun ticket(orderId: Long): Ticket = withContext(Dispatchers.IO) {
        val deviceId = store.requestDeviceId()
        val order = get("/restapi/order/v1/$orderId?deviceId=${URLEncoder.encode(deviceId, "UTF-8")}", deviceId)
        val tickets = order.optJSONArray("tickets") ?: JSONArray()
        var t: JSONObject? = null
        for (i in 0 until tickets.length()) {
            val e = tickets.getJSONObject(i)

            t = e.optJSONObject("mvbTicket") ?: e.optJSONObject("vrrTicket")
            if (t != null) break
        }
        t ?: throw ApiException("Die Bestellung $orderId enthält kein Ticket.")

        val person = t.optJSONObject("personalisierung") ?: t.optJSONObject("personalization")
        Ticket(
            ticketId = t.str("ticketId"),
            productName = t.str("productName"),
            price = t.str("price"),
            gueltigBis = t.str("gueltigBis"),
            zeitlicheGueltigkeit = t.str("zeitlicheGueltigkeit"),
            raeumlicheGueltigkeit = t.str("raeumlicheGueltigkeit"),
            tarif = t.str("tarif"),
            html = t.str("ticketHtml")?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) },
            pdf = t.str("printTicket")?.let { Base64.decode(it, Base64.DEFAULT) },
            holderName = person?.str("name"),
            holderBirth = person?.str("dateOfBirth") ?: t.str("geburtsdatum"),
            status = t.str("ticketGueltigkeit"),
        )
    }

    suspend fun linkSubscription(
        contractNumber: String, firstName: String, lastName: String, dateOfBirth: java.time.LocalDate,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("contractNumber", contractNumber)
            .put("firstName", firstName)
            .put("lastName", lastName)
            .put("dateOfBirth", dateOfBirth.toString())
        call("POST", "/restapi/subscription/v1", body, store.requestDeviceId())
            .optBoolean("isSubscriptionAvailable", false)
    }

    suspend fun unlinkSubscription(subscriptionCode: String): Boolean = withContext(Dispatchers.IO) {
        val code = URLEncoder.encode(subscriptionCode, "UTF-8")
        call("DELETE", "/restapi/subscription/v1?subscriptionCode=$code", null, store.requestDeviceId())
            .optBoolean("success", false)
    }

    private fun accessToken(deviceId: String): String {
        if (store.deviceLocked()) throw ApiException("Zum Aktualisieren das Telefon entsperren.")
        val a = store.accessToken
        if (a != null && jwtExp(a) - System.currentTimeMillis() / 1000 > REFRESH_SKEW_S) return a
        return refresh(deviceId)
    }

    private fun refresh(deviceId: String): String {
        val rt = store.refreshToken
            ?: run { store.logout(); throw ApiException("Nicht angemeldet.", loggedOut = true) }
        if (CLIENT_ID.isEmpty()) throw ApiException("Dieser Build hat keine MVB-Client-Zugangsdaten.")
        val basic = Base64.encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP)
        val body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(rt, "UTF-8")
        val (code, text) = request(
            "POST", "$BASE/restapi/oauth/token",
            mapOf("Authorization" to "Basic $basic", "Content-Type" to "application/x-www-form-urlencoded"),
            body.toByteArray(),
            deviceId,
        )
        if (code == 400 || code == 401) {
            store.logout()
            throw ApiException("Der Refresh-Token wurde abgelehnt. Bitte neu anmelden.", loggedOut = true)
        }
        if (code != 200) throw ApiException("Token-Refresh fehlgeschlagen (HTTP $code).")
        val j = JSONObject(text)
        val access = j.getString("access_token")

        store.saveTokens(access, j.str("refresh_token") ?: rt)
        return access
    }

    private fun get(path: String, deviceId: String): JSONObject = call("GET", path, null, deviceId)

    private fun call(method: String, path: String, json: JSONObject?, deviceId: String): JSONObject {
        var (code, text) = authed(method, path, json, accessToken(deviceId), deviceId)
        if (code == 401) {
            store.accessToken = null
            val retry = authed(method, path, json, refresh(deviceId), deviceId)
            code = retry.first; text = retry.second
        }
        if (code == 401) {
            store.logout()
            throw ApiException("Nicht autorisiert. Bitte neu anmelden.", loggedOut = true)
        }
        if (code !in 200..299) throw ApiException("Anfrage fehlgeschlagen (HTTP $code).")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun authed(method: String, path: String, json: JSONObject?, access: String, deviceId: String) =
        request(
            method, BASE + path,
            buildMap {
                put("Authorization", "Bearer $access")
                if (json != null) put("Content-Type", "application/json; charset=UTF-8")
            },
            json?.toString()?.toByteArray(Charsets.UTF_8),
            deviceId,
        )

    private fun request(
        method: String, url: String, headers: Map<String, String>, body: ByteArray?, deviceId: String,
    ): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.useCaches = false

            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept-Language", "de-DE")
            c.setRequestProperty("X-TAF-DEVICE-ID", deviceId)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } catch (e: java.io.IOException) {
            throw ApiException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            c.disconnect()
        }
    }
}

fun jwtExp(token: String): Long = try {
    val seg = token.split('.')[1]
    val json = String(Base64.decode(seg, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
    JSONObject(json).optLong("exp", 0)
} catch (e: Exception) {
    0
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
