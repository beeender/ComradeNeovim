package org.beeender.comradeneovim.core

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.beeender.comradeneovim.ComradeNeovimPlugin
import org.beeender.comradeneovim.ComradeNeovimService
import org.beeender.comradeneovim.ComradeScope
import java.util.concurrent.ConcurrentHashMap

object NvimInstanceManager {

    private val instanceMap = ConcurrentHashMap<NvimInfo, NvimInstance>()
    private val log = Logger.getInstance(NvimInfoCollector::class.java)

    /**
     * Start monitoring nvim instances.
     */
    fun start() {
        NvimInfoCollector.start {
            if (ComradeNeovimPlugin.autoConnect) connect(it)
        }
    }

    /**
     * Stop monitoring nvim instances and close all the nvim connections.
     */
    fun stop() {
        NvimInfoCollector.stop()
        instanceMap.forEach { it.value.close() }
        instanceMap.clear()
    }

    /**
     * Try to load all nvim instances' current buffers if it is contained by the opened JetBrains' projects.
     */
    fun refresh() {
        val instances = instanceMap.values
        ComradeScope.launch {
            instances.forEach {
                if (it.connected) it.bufManager.loadCurrentBuffer()
            }
        }
    }

    /**
     * Lists all the running nvim instances and their connection status.
     *
     * @return List of running [NvimInfo] and its connection status.
     */
    fun list() : List<Pair<NvimInfo, Boolean>> {
        val instances = instanceMap.toMap()
        return NvimInfoCollector.all.map { Pair(it, instances.containsKey(it)) }
    }

    /**
     * Try to connect to all running nvim instances.
     */
    fun connectAll() {
        NvimInfoCollector.all.forEach { connect(it) }
    }

    /**
     * Disconnect from any nvim instances.
     */
    fun disconnectAll() {
        val infoSet = instanceMap.keys
        infoSet.forEach { disconnect(it) }
    }

    private fun isCompatible(nvimInfo: NvimInfo) : Boolean {
        val majorVersion = ComradeNeovimPlugin.version.split(".")[0].toInt()
        return majorVersion == nvimInfo.majorVersion
    }

    /**
     * Connect to the given nvim.
     */
    fun connect(nvimInfo: NvimInfo) {
        if (instanceMap.containsKey(nvimInfo)) return
        if (!isCompatible(nvimInfo)) {
            ComradeNeovimService.instance.showBalloon(
                    "Failed to connect to Neovim instance ${nvimInfo.address}.\n" +
                            "Incompatible FatBrain version '${nvimInfo.versionString}'",
                    NotificationType.ERROR)
            return
        }

        val address = nvimInfo.address
        try {
            val instance = NvimInstance(address) {
                onStop(nvimInfo)
            }
            instanceMap[nvimInfo] = instance
            val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                ComradeNeovimService.showBalloon("Failed to connect to nvim '$address': $exception",
                        NotificationType.ERROR)
                instanceMap.remove(nvimInfo)?.close()
            }
            ComradeScope.launch(exceptionHandler) {
                instance.connect()
                instance.bufManager.loadCurrentBuffer()
                ComradeNeovimService.instance.showBalloon("Connected to Neovim instance $address",
                        NotificationType.INFORMATION)
            }
            log.info("Try to connect to Neovim instance '$nvimInfo'.")
        } catch (t: Throwable) {
            log.warn("Failed to create Neovim instance for $nvimInfo", t)
            instanceMap.remove(nvimInfo)?.close()
        }
    }

    /**
     * Disconnect from the given nvim.
     */
    fun disconnect(nvimInfo: NvimInfo) {
        log.debug("disconnect: Nvim '${nvimInfo.address}'")
        instanceMap.remove(nvimInfo)?.close()
    }

    private fun onStop(nvimInfo: NvimInfo) {
        log.info("onStop: Nvim '${nvimInfo.address}' has been disconnected.")
        instanceMap.remove(nvimInfo)?.close()
    }
}
