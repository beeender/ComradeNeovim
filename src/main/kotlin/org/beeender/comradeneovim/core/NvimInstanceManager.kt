package org.beeender.comradeneovim.core

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import org.beeender.comradeneovim.ComradeNeovimPlugin
import org.beeender.comradeneovim.ComradeNeovimService
import java.io.File

private const val CONFIG_DIR_NAME = ".ComradeNeovim"
private var HOME = System.getenv("HOME")

class NvimInstancePresentation(val address: String, val currentBufName: String, val connected: Boolean)

object NvimInstanceManager {
    val configDir = File(HOME, CONFIG_DIR_NAME)

    private val instanceMap = HashMap<String, NvimInstance>()
    private val log = Logger.getInstance(NvimInstanceWatcher::class.java)

    fun start() {
        NvimInstanceWatcher.start {
            if (ComradeNeovimPlugin.autoConnect) connectWithPidFile(it)
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
        GlobalScope.launch {
            instances.forEach {
                if (it.connected) it.bufManager.loadCurrentBuffer()
            }
        }
    }

    fun list() : List<NvimInstancePresentation> {
        val ret = mutableListOf<NvimInstancePresentation>()
        val instances = synchronized(this) { instanceMap.toMap() }

        if (!configDir.isDirectory) {
            return ret
        }

        configDir.walk().forEach { file ->
            if (file.isDirectory) return@forEach

            val lines = file.readLines()
            if (lines.isEmpty()) return@forEach
            val address = lines.first()
            if (address.isBlank()) return@forEach

            try {
                runBlocking {
                    val existing = instances.containsKey(address)
                    val instance = instanceMap[address] ?: NvimInstance(address) {}
                    if (existing) instance.connect()
                    val bufName = instance.getCurrentBufName()
                    ret.add(NvimInstancePresentation(address, bufName, existing))
                    if (!existing) instance.close()
                }
            } catch (t : Throwable) {
                log.info("Failed to probe nvim instance $address.", t)
            }
        }
        return ret
    }

    private fun connectWithPidFile(file: File) : NvimInstance? {
        if (!file.isFile) return null

        val lines = file.readLines()
        if (!lines.isEmpty()) {
            val address = lines.first()
            if (!address.isBlank()) {
                return connect(address)
            }
        }
        return null
    }

    fun connectAll() {
        if (!configDir.isDirectory) {
            return
        }

        configDir.walk().forEach {
            connectWithPidFile(it)
        }
    }

    fun disconnectAll() {
        val instances = instanceMap.values
        instances.forEach { it.close() }
    }

    @Synchronized
    fun connect(address: String) : NvimInstance? {
        if (!instanceMap.containsKey(address)){
            return try {
                val instance = NvimInstance(address) {
                    onStop(address)
                }
                instanceMap[address] = instance
                val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                    log.info("Failed to connect to Neovim instance '$address'.", exception)
                    instanceMap.remove(address)?.close()
                }
                GlobalScope.async (exceptionHandler) {
                    instance.connect()
                    instance.bufManager.loadCurrentBuffer()
                    ComradeNeovimService.instance.showBalloon("Connected to Neovim instance $address",
                            NotificationType.INFORMATION)
                }.invokeOnCompletion {
                    log.info("Connected to Neovim instance '$address' with channel ID '${instance.apiInfo.channelId}'.")
                }
                log.info("Try to connect to Neovim instance '$address'.")
                instance
            } catch (t: Throwable) {
                log.warn("Failed to create Neovim instance for $address", t)
                instanceMap.remove(address)?.close()
                null
            }
        }
        return null
    }

    @Synchronized
    fun disconnect(address: String) {
        instanceMap[address]?.close()
    }

    @Synchronized
    private fun onStop(address: String) {
        instanceMap.remove(address)?.close()
    }
}
