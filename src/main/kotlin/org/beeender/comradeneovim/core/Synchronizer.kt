package org.beeender.comradeneovim.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.beeender.comradeneovim.ComradeScope
import org.beeender.neovim.Constants.Companion.FUN_NVIM_BUF_ATTACH
import org.beeender.neovim.Constants.Companion.FUN_NVIM_BUF_GET_CHANGEDTICK
import org.beeender.neovim.Constants.Companion.FUN_NVIM_BUF_SET_LINES
import org.beeender.neovim.Constants.Companion.FUN_NVIM_CALL_FUNCTION

@Suppress("MemberVisibilityCanBePrivate")
class BufferOutOfSyncException(val syncBuffer: SyncBuffer, val nextTick: Int) :
        IllegalStateException () {
    override val message: String?
        get() = "Buffer: ${syncBuffer.id} '${syncBuffer.path}' is out of sync.\n" +
                "Current changedtick is ${syncBuffer.synchronizer.changedtick}, the next changedtick should be $nextTick."
}

private val log = Logger.getInstance(Synchronizer::class.java)

/**
 * Handle both side (JetBrain & Neovim) changes and try to make both side buffers synchronized.
 */
class Synchronizer(private val syncBuffer: SyncBuffer) : DocumentListener {
    var changedtick = -1
        private set
    private val pendingChanges = mutableMapOf<Int, BufferChange>()
    private val nvimInstance = syncBuffer.nvimInstance
    private val client = nvimInstance.client
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        handleException(t)
    }

    private var changeBuilder: BufferChange.JetBrainsChangeBuilder? = null
    private var changedByNvim = false
    var exceptionHandler: ((Throwable) -> Unit)? = null

    fun onChange(change: BufferChange) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        try {
            when (change.source) {
                BufferChange.Source.NEOVIM -> {
                    changedByNvim = true
                    try {
                        onNeovimChange(change)
                    } finally {
                        changedByNvim = false
                    }
                }
                BufferChange.Source.JetBrain -> onJetBrainChange(change)
            }
        }
        catch (t: Throwable) {
            handleException(t)
        }
    }

    fun initFromJetBrain() {
        val bufId = syncBuffer.id
        val lines = syncBuffer.document.charsSequence.split('\n')
        ComradeScope.launch(coroutineExceptionHandler) {
            val result = client.api.callAtomic(listOf(
                    FUN_NVIM_CALL_FUNCTION to
                            listOf(FUN_BUFFER_REGISTER, listOf(bufId, nvimInstance.apiInfo.channelId, lines)),
                    FUN_NVIM_BUF_ATTACH  to listOf(bufId, false, emptyMap<Any, Any>())
            ))
            if (result[1] != null) {
                val e = java.lang.IllegalArgumentException("Register buffer failed. $result")
                handleException(e)
            }
        }
    }

    private fun checkNeovimChangedTick(change: BufferChange) : Boolean {
        val pendingChange = pendingChanges.remove(change.tick)
        // The change is made by JetBrain.
        if (pendingChange != null) {
            return true
        }

        if (changedtick == -1) {
            changedtick = change.tick
            return change.lines == null
        }

        if (changedtick + 1 == change.tick) {
            changedtick++
            return change.lines == null
        }

        throw BufferOutOfSyncException(syncBuffer, change.tick)
    }

    private fun handleException(t: Throwable) {
        if (exceptionHandler != null) {
            exceptionHandler?.invoke(t)
        } else {
            log.error("Exception in Synchronizer", t)
        }
    }

    /**
     * Public for mock.
     */
    fun onJetBrainChange(change: BufferChange) {
        val lines = change.lines ?: throw BufferOutOfSyncException(syncBuffer, change.tick)
        ComradeScope.launch(coroutineExceptionHandler)  {
            val result = client.api.callAtomic(listOf(
                    FUN_NVIM_BUF_SET_LINES to
                            listOf(change.syncBuffer.id, change.firstLine, change.lastLine, true, lines),
                    FUN_NVIM_BUF_GET_CHANGEDTICK to listOf(change.syncBuffer.id)
            ))
            if (result[1] != null) throw IllegalArgumentException(result[1].toString())
            val newChangedtick = (result[0] as List<*>)[1] as Int
            if (newChangedtick != change.tick) throw BufferOutOfSyncException(change.syncBuffer, newChangedtick)
        }
    }

    private fun onNeovimChange(change: BufferChange) {
        // The changedtick event only or the change is initialized from JetBrain, nothing has been changed
        if (checkNeovimChangedTick(change)) return

        // Nullability has been checked in above code
        val lineData = change.lines!!

        val firstLine = change.firstLine
        val lastLine = change.lastLine

        val stringBuilder = StringBuilder()
        lineData.forEachIndexed { index, s ->
            stringBuilder.append(s)
            if (index < lineData.size - 1) {
                stringBuilder.append('\n')
            }
        }
        if (lastLine == -1) {
            syncBuffer.setText(stringBuilder)
        }
        else
        {
            val curLineCount = syncBuffer.document.lineCount
            val document = syncBuffer.document
            // start should include the previous EOL
            when {
                // Deletion
                lineData.isEmpty() -> {
                    val start = when (firstLine) {
                        0 -> 0
                        curLineCount -> document.getLineEndOffset(firstLine - 1)
                        else -> document.getLineStartOffset(firstLine) - 1
                    }
                    val end = when {
                        lastLine > curLineCount -> 0
                        lastLine == curLineCount -> document.getLineEndOffset(lastLine - 1)
                        start == 0 -> document.getLineStartOffset(lastLine)
                        else -> document.getLineStartOffset(lastLine) - 1
                    }
                    syncBuffer.deleteText(start, end)
                }
                firstLine == lastLine -> {
                    val start = when (firstLine) {
                        0 -> 0
                        curLineCount -> document.getLineEndOffset(firstLine - 1)
                        else -> document.getLineStartOffset(firstLine) - 1
                    }
                    if (start != 0) {
                        stringBuilder.insert(0, '\n')
                    }
                    else {
                        stringBuilder.append('\n')
                    }
                    // Insertion
                    syncBuffer.insertText(start,
                            if (firstLine == curLineCount) stringBuilder
                            else stringBuilder)
                }
                else -> {
                    val start = when (firstLine) {
                        0 -> 0
                        curLineCount -> document.getLineEndOffset(firstLine - 1)
                        else -> document.getLineStartOffset(firstLine) - 1
                    }
                    // Replace the whole end line including EOL
                    val end = when {
                        lastLine > curLineCount -> 0
                        lastLine == curLineCount -> document.getLineEndOffset(lastLine - 1)
                        else -> document.getLineEndOffset(lastLine - 1) + 1
                    }
                    if (firstLine != 0) {
                        stringBuilder.insert(0, '\n')
                    }
                    syncBuffer.replaceText(start, end,
                            if (lastLine >= curLineCount) stringBuilder
                            else stringBuilder.append('\n'))
                }
            }
        }
    }

    override fun beforeDocumentChange(event: DocumentEvent) {
        if (changedByNvim) return
        // Should not happen
        assert(changeBuilder == null)

        val builder = BufferChange.JetBrainsChangeBuilder(syncBuffer)
        builder.beforeChange(event)
        changeBuilder = builder
    }

    override fun documentChanged(event: DocumentEvent) {
        if (changedByNvim) return
        val builder = changeBuilder ?: return
        builder.afterChange(event)
        val change = builder.build()
        changedtick++
        pendingChanges[change.tick] = change
        onChange(change)
        changeBuilder = null
    }
}