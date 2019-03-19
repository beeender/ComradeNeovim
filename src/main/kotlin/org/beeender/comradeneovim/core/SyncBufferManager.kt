package org.beeender.comradeneovim.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import org.beeender.comradeneovim.ComradeNeovimPlugin
import org.beeender.comradeneovim.invokeOnMainLater
import org.beeender.neovim.BufChangedtickEvent
import org.beeender.neovim.BufDetachEvent
import org.beeender.neovim.BufLinesEvent
import org.beeender.neovim.Constants.Companion.MSG_NVIM_BUF_CHANGEDTICK_EVENT
import org.beeender.neovim.Constants.Companion.MSG_NVIM_BUF_DETACH_EVENT
import org.beeender.neovim.Constants.Companion.MSG_NVIM_BUF_LINES_EVENT
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.rpc.Notification
import java.util.concurrent.ConcurrentHashMap

class SyncBufferManager(private val nvimInstance: NvimInstance) : Disposable {
    companion object {
        val TOPIC = Topic<SyncBufferManagerListener>(
                "SyncBuffer related events", SyncBufferManagerListener::class.java)
        private val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC)
    }

    private val log = Logger.getInstance(SyncBufferManager::class.java)
    // Although this is a ConcurrentHashMap, all create/delete SyncBuffer operations still have to be happened on the
    // UI thread.
    private val bufferMap = ConcurrentHashMap<Int, SyncBuffer>()
    private val client = nvimInstance.client

    init {
        Disposer.register(nvimInstance, this)
    }

    fun findBufferById(id: Int) : SyncBuffer? {
        return bufferMap[id]
    }

    suspend fun loadCurrentBuffer() {
        val bufId = client.api.getCurrentBuf()
        val path = client.bufferApi.getName(bufId)

        loadBuffer(bufId, path)
    }

    fun loadBuffer(bufId: Int, path: String) {
        invokeOnMainLater {
            doLoadBuffer(bufId, path)
        }
    }

    private fun doLoadBuffer(bufId: Int, path: String) {
        var syncedBuffer = findBufferById(bufId)
        if (syncedBuffer == null) {
            try {
                syncedBuffer = SyncBuffer(bufId, path, nvimInstance)
            } catch (e: BufferNotInProjectException) {
                log.debug("'$path' is not a part of any opened projects.", e)
                return
            }
            bufferMap[bufId] = syncedBuffer
            syncedBuffer.initSynchronizer()
        }
        if (ComradeNeovimPlugin.showEditorInSync) {
            syncedBuffer.navigate()
        }
    }

    fun releaseBuffer(syncBuffer: SyncBuffer) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        bufferMap.remove(syncBuffer.id)
        syncBuffer.release()
    }

    override fun dispose() {
        val list = bufferMap.map { it.value }
        bufferMap.clear()
        ApplicationManager.getApplication().invokeLater {
            list.forEach {
                releaseBuffer(it)
            }
        }
    }

    /**
     * Clean up all resources which are related to the given project.
     */
    fun cleanUp(project: Project) {
        val entriesToRemove = bufferMap
            .filter { it.value.project == project }
        bufferMap.keys.removeAll(entriesToRemove.map { it.key })
        ApplicationManager.getApplication().invokeAndWait {
            entriesToRemove.forEach {
                releaseBuffer(it.value)
            }
        }
    }

    @NotificationHandler(MSG_COMRADE_BUF_ENTER)
    fun comradeBufEnter(notification: Notification) {
        val param = ComradeBufEnterParams.fromNotification(notification)
        loadBuffer(param.id, param.path)
    }

    @NotificationHandler(MSG_NVIM_BUF_LINES_EVENT)
    fun nvimBufLinesEvent(event: BufLinesEvent) {
        val buf = findBufferById(event.id) ?: return
        invokeOnMainLater {
            val change = BufferChange.NeovimChangeBuilder(buf, event).build()
            buf.synchronizer.onChange(change)
            publisher.bufferSynced(buf)
        }
    }

    @NotificationHandler(MSG_NVIM_BUF_CHANGEDTICK_EVENT)
    fun nvimBufChangedtickEvent(event: BufChangedtickEvent) {
        val buf = findBufferById(event.id) ?: return
        invokeOnMainLater {
            val change = BufferChange.NeovimChangeBuilder(buf, event).build()
            buf.synchronizer.onChange(change)
        }
    }

    @NotificationHandler(MSG_NVIM_BUF_DETACH_EVENT)
    fun nvimBufDetachEvent(event: BufDetachEvent) {
        val buf = findBufferById(event.id) ?: return
        invokeOnMainLater {
            releaseBuffer(buf)
        }
    }

    @NotificationHandler(MSG_COMRADE_BUF_WRITE)
    fun comradeBufWrite(event: ComradeBufWriteParams)
    {
        val syncedBuffer = findBufferById(event.id) ?: return
        invokeOnMainLater {
            FileDocumentManager.getInstance().saveDocument(syncedBuffer.document)
        }
    }
}