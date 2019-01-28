package org.beeender.comradeneovim

private val IPV4_REGEX = "^([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+):([0-9]+)".toRegex()

fun isIPV4String(address: String) : Boolean {
    return IPV4_REGEX.matches(address)
}

fun parseIPV4String(address: String) : Pair<String, Int>? {
    val matchResult = IPV4_REGEX.find(address) ?: return null
    return Pair(matchResult.groupValues[1], matchResult.groupValues[2].toInt())
}
