package com.succ.antitrack.magdeburg

import android.net.Uri

data class Tokens(val access: String?, val refresh: String)

fun parseLogin(input: String): Tokens? {
    val s = input.trim()
    if (s.isEmpty()) return null
    if ("refreshToken=" in s) {
        val query = s.substringAfter('?', s)
        val uri = Uri.parse("prod.mvbapp://login?$query")
        return fromUri(uri)
    }

    if (s.count { it == '.' } == 2 && s.none { it.isWhitespace() }) return Tokens(null, s)
    return null
}

fun fromUri(uri: Uri): Tokens? {
    val refresh = uri.getQueryParameter("refreshToken")?.takeIf { it.isNotBlank() } ?: return null
    val access = uri.getQueryParameter("accessToken")?.takeIf { it.isNotBlank() }
    return Tokens(access, refresh)
}
