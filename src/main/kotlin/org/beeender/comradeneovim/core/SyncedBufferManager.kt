package org.beeender.comradeneovim.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.beeender.comradeneovim.ComradeNeovimPlugin
import org.beeender.comradeneovim.ComradeScope
import org.beeender.comradeneovim.insight.InsightProcessor
import org.beeender.neovim.BufLinesEvent
import org.beeender.neovim.BufferApi
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.rpc.Notification
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class SyncedBufferManager(private val nvimInstance: NvimInstance) : Closeable {
    private class BufferPack(val buffer: SyncedBuffer, val insightProcessor: InsightProcessor)
    private val log = Logger.getInstance(SyncedBufferManager::class.java)
    // Although this is a ConcurrentHashMap, all create/delete SyncedBuffer operations still have to be happened on the
    // UI thread.
    private val bufferMap = ConcurrentHashMap<Int, BufferPack>()
    private val client = nvimInstance.client

    fun findBufferById(id: Int) : SyncedBuffer? {
        return bufferMap[id]?.buffer
    }

    suspend fun loadCurrentBuffer() {
        val bufId = client.api.getCurrentBuf()
        val path = client.bufferApi.getName(bufId)

        loadBuffer(bufId, path)
    }

    private fun createAttachExceptionHandler(bufId: Int) : CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            log.info("Failed to attach to buffer '$bufId'", exception)
            bufferMap.remove(bufId)
        }
    }

    private fun loadBuffer(bufId: Int, path: String) {
        ApplicationManager.getApplication().invokeLater {
            var syncedBuffer = findBufferById(bufId)
            if (syncedBuffer == null) {
                try {
                    syncedBuffer = SyncedBuffer(bufId, path)
                } catch (e : BufferNotInProjectException) {
                    log.debug("'$path' is not a part of any opened projects.", e)
                    return@invokeLater
                }
                bufferMap[bufId] = BufferPack(syncedBuffer, InsightProcessor(nvimInstance, syncedBuffer))
                ComradeScope.launch(createAttachExceptionHandler(bufId)) {
                    withTimeout(2000) {
                        client.api.callFunction(FUN_BUFFER_REGISTER, listOf(bufId, nvimInstance.apiInfo.channelId))
                        client.bufferApi.attach(bufId, true)
                    }
                    log.info("'$path' has been loaded as a synced buffer.")
                }
            }
            if (ComradeNeovimPlugin.showEditorInSync) {
                syncedBuffer.navigate()
            }
        }
    }

    private fun reloadBuffer(id: Int) {
        val buf = findBufferById(id) ?: return
        if (buf.detached) return
        buf.detached = true
        GlobalScope.launch(createAttachExceptionHandler(id)) {
            client.bufferApi.detach(id)
        }
    }

    private suspend fun validate(id: Int) : Boolean {

        val buf = findBufferById(id) ?: return true
        val lineCount = buf.document.lineCount
        // I don't want to deal with the annoy line rules differences.
        if (lineCount < 2) return true

        val ret = client.api.callAtomic(listOf(
                "nvim_get_current_buf" to emptyList(),
                "nvim_buf_line_count" to listOf(id)
        ))
        if (ret[1] != null) {
            log.warn("The buffer $id is out of sync. Remote exception:\n ${ret[1]}")
        }
        val results = ret[0] as List<*>
        val curBuf = BufferApi.decodeBufId(results[0])
        if (curBuf != id) {
            return true
        }
        if (lineCount != results[1] as Int) {
            log.warn("The buffer $id is out of sync. $results")
            return false
        }
        log.info("The buffer $id has been verified")
        return true
    }

    override fun close() {
        val list = bufferMap.map { it.value.buffer }
        bufferMap.clear()
        ApplicationManager.getApplication().invokeLater {
            list.forEach {
                it.close()
            }
        }
    }

    /**
     * Clean up all resources which are related to the given project.
     */
    fun cleanUp(project: Project) {
        val entriesToRemove = bufferMap
            .filter { it.value.buffer.project == project }
        bufferMap.keys.removeAll(entriesToRemove.map { it.key })
        ApplicationManager.getApplication().invokeAndWait {
            entriesToRemove.forEach {
                it.value.buffer.close()
            }
        }
    }

    @NotificationHandler("comrade_buf_enter")
    fun bufEnter(notification: Notification) {
        val map = notification.args.first() as Map<*, *>
        val id = map["id"] as Int
        val path = map["path"] as String
        loadBuffer(id, path)
    }

    private var verifyJob: Deferred<Unit>? = null

    @NotificationHandler("nvim_buf_lines_event")
    fun nvimBufLines(notification: Notification) {
        val event = BufLinesEvent(notification)
        val buf = findBufferById(event.id) ?: return
        ApplicationManager.getApplication().invokeLater {
            buf.onBufferChanged(event)
            bufferMap[event.id]?.insightProcessor?.process()
        }
        verifyJob?.cancel()
        verifyJob = ComradeScope.async {
            delay(5000)
            if (!validate(event.id)) {
                reloadBuffer(event.id)
            }
        }
    }

    @NotificationHandler("nvim_buf_detach_event")
    fun nvimBufDetachEvent(notification: Notification) {
        val bufId = BufferApi.decodeBufId(notification)
        val buf = findBufferById(bufId) ?: return
        if (buf.detached) {
            buf.detached = false
            ComradeScope.launch(createAttachExceptionHandler(bufId)) {
                client.bufferApi.attach(buf.id, true)
            }
        } else {
            bufferMap.remove(buf.id)
            ApplicationManager.getApplication().invokeLater {
                buf.close()
            }
        }
    }
}
