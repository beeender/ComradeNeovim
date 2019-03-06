package org.beeender.comradeneovim.core

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.isDirectory
import org.beeender.comradeneovim.isIPV4String
import java.io.File
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.file.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

private val INSTANCE_WATCHER_THREAD_FACTORY = ThreadFactoryBuilder()
        .setNameFormat("ComradeNeovim-Watcher-%d").build()
private const val CONFIG_DIR_NAME = ".ComradeNeovim"
private var HOME = System.getenv("HOME")
private val CONFIG_DIR= File(HOME, CONFIG_DIR_NAME)

/**
 * Information about the running neovim instances on the system.
 * @param pid nvim instance pid.
 * @param address nvim listen address.
 * @param versionString ComradeFatBrains version.
 * @param startDir the starting director where nvim starts at.
 */
class NvimInfo (val pid: Int,
                val address: String,
                val versionString: String,
                val startDir: String) {
    val majorVersion: Int
    val minorVersion: Int
    val patchVersion: Int
    init {
        val versions = versionString.split(".")
        if (versions.size < 3) {
            majorVersion = -1
            minorVersion = -1
            patchVersion = -1
        } else {
            majorVersion = versions[0].toIntOrNull() ?: -1
            minorVersion = versions[1].toIntOrNull() ?: -1
            patchVersion = versions[2].toIntOrNull() ?: -1
        }
    }

    override fun toString(): String {
        return "Nvim Listen Address: $address, Start Directory: $startDir, ComradeFatBrains Version: $versionString"
    }
}

/**
 * Monitor the system to collect all the running Neovim instance information.
 */
internal object NvimInfoCollector {
    private val watcher = FileSystems.getDefault().newWatchService()
    private val watchPath = Paths.get(CONFIG_DIR.canonicalPath)
    private val executor = Executors.newSingleThreadExecutor(INSTANCE_WATCHER_THREAD_FACTORY)
    private val log = Logger.getInstance(NvimInfoCollector::class.java)
    private val backingAll = CopyOnWriteArrayList<NvimInfo>()
    private var started = false

    /**
     * All running nvim instances's information.
     */
    val all:List<NvimInfo>
        get() {
            cleanNonExisting()
            return backingAll
        }

    fun start(callback: (NvimInfo) -> Unit) {
        if (started) throw IllegalStateException("NvimInfoCollector has been started already.")
        started = true
        if (!watchPath.isDirectory()) throw IllegalArgumentException("'$watchPath' is not a directory.")

        executor.submit {
            watchPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE)
            // Walk the directory first before any new nvim instances started.
            watchPath.toFile().walk().forEach { file ->
                val nvimInfo = parseInfoFile(file)
                nvimInfo?.let { callback(it) }
            }
            while(!Thread.interrupted() && !executor.isTerminated) {
                try {
                    val key = watcher.take()
                    key.pollEvents().forEach { event ->
                        val path = event.context()
                        if (path is Path)  {
                            log.info("New neovim instance started: $path")
                            val nvimInfo = parseInfoFile(watchPath.resolve(path).toFile())
                            nvimInfo?.let { callback(it) }
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

    private fun parseInfoFile(file: File) : NvimInfo? {
        if (!file.isFile) return null

        val lines = file.readLines()
        if (lines.isEmpty() || lines.size < 3) return null

        val pid = try {
            file.nameWithoutExtension.toInt()
        } catch (e: NumberFormatException) {
            return null
        }
        val address = lines.first()
        if (!checkAddress(address)) return null

        val version = lines[1]
        val startDir = lines[2]

        if (all.firstOrNull { it.pid == pid} != null) {
            log.warn("NvimInfo with pid '$pid' has been discovered before.")
            return null
        }

        val info = NvimInfo(pid, address, version, startDir)
        backingAll.add(info)
        return info
    }

    private fun cleanNonExisting() {
        backingAll.removeIf {
            !checkAddress(it.address)
        }
    }

    private fun checkAddress(address: String) : Boolean {
        if (!isIPV4String(address)) {
            val file = File(address)
            return file.exists()
        }
        return true
    }

    fun stop() {
        executor.shutdownNow()
        watcher.close()
    }
}
