package org.beeender.comradeneovim.core

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.*
import java.util.concurrent.Executors

internal object NvimInstanceWatcher {
    private val watcher = FileSystems.getDefault().newWatchService()
    private val path = Paths.get(NvimInstanceManager.configDir.canonicalPath)
    private val executor = Executors.newSingleThreadExecutor()
    private val log = Logger.getInstance(NvimInstanceWatcher::class.java)

    fun start(callback: (File) -> Unit) {
        executor.submit {
            path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE)
            while(!Thread.interrupted() && !executor.isTerminated) {
                try {
                    val key = watcher.take()
                    key.pollEvents().forEach {
                        val name = it.context()
                        if (name is Path)  {
                            log.info("New neovim instance started: $name")
                            callback(path.resolve(name).toFile())
                        }
                    }
                    key.reset()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: ClosedWatchServiceException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    fun stop() {
        executor.shutdownNow()
        watcher.close()
    }
}