package org.beeender.comradeneovim.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.beeender.neovim.BufLinesEvent
import org.beeender.neovim.Client
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.rpc.Notification
import java.lang.StringBuilder
import java.util.concurrent.ConcurrentHashMap

class SyncedBufferManager(private val client: Client) {
    private val log = Logger.getInstance(SyncedBufferManager::class.java)
    private val bufferMap = ConcurrentHashMap<String, SyncedBuffer>()

    fun findBufferByPath(path: String) : SyncedBuffer? {
        return bufferMap[path]
    }

    fun findBufferById(id: Int) : SyncedBuffer? {
        val values = bufferMap.values
        values.forEach {
            if (it.id == id) {
                return it
            }
        }
        return null
    }

    fun loadCurrentBuffer() {
        val bufId = client.api.getCurrentBuf()
        val path = client.bufferApi.getName(bufId)

        loadBuffer(bufId, path)
    }

    fun loadBuffer(id: Int, path: String) {
        if (path.isBlank()) return;

        ApplicationManager.getApplication().invokeLater {
            if (findBufferByPath(path) != null) return@invokeLater

            add(SyncedBuffer(id, path))
            client.bufferApi.attach(id, true)
        }
    }

    private fun add(buffer: SyncedBuffer) {
        bufferMap[buffer.path] = buffer
    }

    @NotificationHandler("IntelliJBufEnter")
    fun bufEnter(notification: Notification) {
        val map = notification.args.first() as Map<*, *>
        val id = map["id"] as Int
        val path = map["path"] as String
        loadBuffer(id, path)
    }

    @NotificationHandler("nvim_buf_lines_event")
    fun nvimBufLines(notification: Notification) {
        val event = BufLinesEvent(notification.args as ArrayList<Any?>)
        ApplicationManager.getApplication().invokeLater {
            val buf = findBufferById(event.id)
            buf?.onBufferChanged(event)
            val list = client.bufferApi.getLines(event.id, 0, -1, false)
            val sb = StringBuilder()
            list.forEachIndexed { index, s ->
                sb.append(s)
                if (index < list.size - 1) {
                    sb.append('\n')
                }
            }
            if (sb.toString() != buf?.text) {
                log.info("wrong")
                log.info(sb.toString())
                log.info(buf?.text)
            }
        }
    }
}
