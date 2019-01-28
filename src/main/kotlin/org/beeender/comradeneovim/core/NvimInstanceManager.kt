package org.beeender.comradeneovim.core

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val CONFIG_DIR_NAME = ".IntelliNeovim"
private var HOME = System.getenv("HOME")
private val IPV4_REGEX = "^([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+):([0-9]+)".toRegex()

object NvimInstanceManager {
    val configDir = File(HOME, CONFIG_DIR_NAME)

    private val instanceMap = ConcurrentHashMap<String, NvimInstance>()
    private val log = Logger.getInstance(NvimInstanceWatcher::class.java)

    fun start() {
        NvimInstanceWatcher.start { connectWithPidFile(it)}
        connectAll()
    }

    fun stop() {
        NvimInstanceWatcher.stop()
    }

    private fun connectWithPidFile(file: File) {
        val lines = file.readLines()
        if (!lines.isEmpty()) {
            val address = lines.first()
            if (!address.isBlank()) {
                connect(address)
            }
        }
    }

    private fun connectAll() {
        if (!configDir.isDirectory) {
            return
        }

        configDir.walk().forEach {
            if (it.isFile) {
                val lines = it.readLines()
                if (lines.isEmpty()) return@forEach

                val address = lines.first()
                if (!address.isBlank()) {
                    connect(address)
                }
            }
        }
    }

    private fun connect(address: String) {
        if (!instanceMap.containsKey(address)){
            if (IPV4_REGEX.matches(address) || File(address).exists()) {
                try {
                    val instance = NvimInstance(address)
                    instanceMap[address] = instance
                    log.info("Connected to Neovim instance '$address' with channel ID '${instance.apiInfo.channelId}'.")
                } catch (t: Throwable) {
                    log.error("Failed to create Neovim instance for $address", t)
                }
            }
        }
    }
}