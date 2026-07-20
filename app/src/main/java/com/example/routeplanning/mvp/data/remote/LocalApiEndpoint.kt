package com.example.routeplanning.mvp.data.remote

import java.net.URI

internal fun isAllowedApiEndpoint(uri: URI, allowCleartext: Boolean): Boolean {
    if (uri.scheme.equals("https", ignoreCase = true)) {
        return true
    }
    return allowCleartext &&
        uri.scheme.equals("http", ignoreCase = true) &&
        uri.host.isLocalDevelopmentHost()
}

private fun String?.isLocalDevelopmentHost(): Boolean {
    val normalizedHost = this?.lowercase() ?: return false
    if (normalizedHost == "localhost") {
        return true
    }

    val octets = normalizedHost.split('.').map { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) {
        return false
    }

    val first = octets[0]!!
    val second = octets[1]!!
    return first == 10 ||
        first == 127 ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168)
}
