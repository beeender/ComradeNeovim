package org.beeender.comradeneovim.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import org.beeender.neovim.BufLinesEvent
import org.beeender.neovim.BufferApi
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.rpc.Notification
import org.beeender.comradeneovim.ComradeNeovimPlugin

class SyncedBufferManager(private val nvimInstance: NvimInstance) {
    private val log = Logger.getInstance(SyncedBufferManager::class.java)
    private val bufferMap = HashMap<Int, SyncedBuffer>()
    private val client = nvimInstance.client

    @Synchronized
    fun findBufferById(id: Int) : SyncedBuffer? {
        return bufferMap[id]
    }

    suspend fun loadCurrentBuffer() {
        val bufId = client.api.getCurrentBuf()
        val path = client.bufferApi.getName(bufId)

        loadBuffer(bufId, path)
    }

    private fun loadBuffer(id: Int, path: String) {
        ApplicationManager.getApplication().invokeLater {
            synchronized(this) {
                try {
                    val syncedBuffer = bufferMap[id] ?: SyncedBuffer(id, path)
                    if (!bufferMap.containsKey(id)) {
                        bufferMap[id] = syncedBuffer
                        runBlocking {
                            client.api.callFunction("ComradeRegisterBuffer", listOf(id, nvimInstance.apiInfo.channelId))
                        }
                        client.bufferApi.attach(id, true)
                        log.info("'$path' has been loaded as a synced buffer.")
                    }
                    if (ComradeNeovimPlugin.showEditorInSync) {
                        syncedBuffer.navigate()
                    }
                } catch (e : BufferNotInProjectException) {
                    log.debug("'$path' is not a part of any opened projects.", e)
                }
            }
        }
    }

    private fun reloadBuffer(id: Int) {
        val buf = findBufferById(id) ?: return
        synchronized(this) {
            if (buf.detached) return
            buf.detached = true
        }
        client.bufferApi.detach(id)
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
        ApplicationManager.getApplication().invokeLater {
            val buf = findBufferById(event.id) ?: return@invokeLater
            buf.onBufferChanged(event)
            verifyJob?.cancel()
            verifyJob = GlobalScope.async{
                delay(5000)
                if (!validate(event.id)) {
                    reloadBuffer(event.id)
                }
            }
        }
    }

    @NotificationHandler("nvim_buf_detach_event")
    fun nvimBufDetachEvent(notification: Notification) {
        val bufId = BufferApi.decodeBufId(notification)
        ApplicationManager.getApplication().invokeLater {
            synchronized(this) {
                val buf = findBufferById(bufId) ?: return@invokeLater
                if (buf.detached) {
                    client.bufferApi.attach(buf.id, true)
                } else {
                    bufferMap.remove(buf.id)
                    buf.close()
                }
            }
        }
    }
}
