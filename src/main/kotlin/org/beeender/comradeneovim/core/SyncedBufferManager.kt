package org.beeender.comradeneovim.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.beeender.neovim.BufLinesEvent
import org.beeender.neovim.BufferApi
import org.beeender.neovim.Client
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.rpc.Notification

class SyncedBufferManager(private val client: Client) {
    private val log = Logger.getInstance(SyncedBufferManager::class.java)
    private val bufferMap = HashMap<String, SyncedBuffer>()

    @Synchronized
    fun findBufferByPath(path: String) : SyncedBuffer? {
        return bufferMap[path]
    }

    @Synchronized
    private fun findBufferById(id: Int) : SyncedBuffer? {
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

    private fun loadBuffer(id: Int, path: String) {
        ApplicationManager.getApplication().invokeLater {
            synchronized(this) {
                if (bufferMap.containsKey(path)) return@invokeLater

                try {
                    val syncedBuffer = SyncedBuffer(id, path)
                    bufferMap[path] = syncedBuffer
                    client.bufferApi.attach(id, true)
                    log.info("'$path' has been loaded as a synced buffer.")
                } catch (e : BufferNotInProjectException) {
                    log.info("'$path' is not a part of any opened projects.")
                    log.debug(e)
                }
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

    @NotificationHandler("nvim_buf_lines_event")
    fun nvimBufLines(notification: Notification) {
        val event = BufLinesEvent(notification)
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

    @NotificationHandler("nvim_buf_detach_event")
    fun nvimBufDetachEvent(notification: Notification) {
        val bufId = BufferApi.decodeBufId(notification)
        ApplicationManager.getApplication().invokeLater {
            synchronized(this) {
                val buf = findBufferById(bufId) ?: return@invokeLater
                bufferMap.remove(buf.path)
                buf.close()
            }
        }
    }
}
