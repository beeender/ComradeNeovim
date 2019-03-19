package org.beeender.comradeneovim

import com.intellij.openapi.application.ApplicationManager

private val IPV4_REGEX = "^([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+):([0-9]+)".toRegex()

fun isIPV4String(address: String) : Boolean {
    return IPV4_REGEX.matches(address)
}

fun parseIPV4String(address: String) : Pair<String, Int>? {
    val matchResult = IPV4_REGEX.find(address) ?: return null
    return Pair(matchResult.groupValues[1], matchResult.groupValues[2].toInt())
}

/**
 * If the caller is on the UI thread, just invoke the given [Runnable]. Mostly for the tests.
 * Otherwise invoke it later on the UI thread.
 */
fun invokeOnMainLater(runnable: () -> Unit) {
    if (ApplicationManager.getApplication().isDispatchThread) {
        runnable.invoke()
    } else {
        ApplicationManager.getApplication().invokeLater(runnable)
    }
}

fun invokeOnMainAndWait(runnable: () -> Unit, exceptionHandler: ((Throwable) -> Unit)? = null) {
    var throwable: Throwable? = null
    ApplicationManager.getApplication().invokeAndWait {
        try {
            runnable.invoke()
        }
        catch (t: Throwable) {
            throwable = t
        }
    }
    val toThrow = throwable ?: return
    if (exceptionHandler == null) {
        throw toThrow
    }
    else {
        exceptionHandler.invoke(toThrow)
    }
}
