package org.beeender.comradeneovim.core

import com.intellij.openapi.diagnostic.Logger
import java.io.File

private const val CONFIG_DIR_NAME = ".ComradeNeovim"
private var HOME = System.getenv("HOME")

object NvimInstanceManager {
    val configDir = File(HOME, CONFIG_DIR_NAME)

    private val instanceMap = HashMap<String, NvimInstance>()
    private val log = Logger.getInstance(NvimInstanceWatcher::class.java)

    fun start() {
        NvimInstanceWatcher.start {
            connectWithPidFile(it)?.bufManager?.loadCurrentBuffer()
        }
        connectAll()
    }

    fun stop() {
        NvimInstanceWatcher.stop()
        synchronized(this) {
            instanceMap.clear()
        }
    }

    fun refresh() {
        val instances = synchronized(this) { instanceMap.values }
        instances.forEach { it.bufManager.loadCurrentBuffer() }
    }

    private fun connectWithPidFile(file: File) : NvimInstance? {
        val lines = file.readLines()
        if (!lines.isEmpty()) {
            val address = lines.first()
            if (!address.isBlank()) {
                return connect(address)
            }
        }
        return null
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

    @Synchronized
    private fun connect(address: String) : NvimInstance? {
        if (!instanceMap.containsKey(address)){
            return try {
                val instance = NvimInstance(address) {
                    onStop(address)
                }
                instanceMap[address] = instance
                log.info("Connected to Neovim instance '$address' with channel ID '${instance.apiInfo.channelId}'.")
                instance
            } catch (t: Throwable) {
                log.error("Failed to create Neovim instance for $address", t)
                null
            }
        }
        return null
    }

    @Synchronized
    private fun onStop(address: String) {
        instanceMap.remove(address)
    }
}