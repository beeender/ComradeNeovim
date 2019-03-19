package org.beeender.comradeneovim.core

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.TextRange
import org.beeender.neovim.BufChangedtickEvent
import org.beeender.neovim.BufLinesEvent

/**
 * @param firstLine integer line number of the first line that was replaced. Zero-indexed.
 * @param lastLine integer line number of the first line that was not replaced (exclusive).
 * @param lines list of strings contains the content of new buffer lines.
 * @param source which side initialized this change request.
 */
class BufferChange private constructor(val syncBuffer: SyncBuffer,
                                       val firstLine: Int,
                                       val lastLine: Int,
                                       val lines: List<String>?,
                                       val source: Source,
                                       val tick: Int) {
    enum class Source {
        NEOVIM,
        JetBrain
    }

    fun contentEquals(other: BufferChange) : Boolean {
        return firstLine == other.firstLine &&
                lastLine == other.lastLine &&
                tick == other.tick &&
                lines == other.lines
    }

    class NeovimChangeBuilder {
        private val tick: Int
        private val lines: List<String>?
        private val syncBuffer: SyncBuffer
        private val firstLine: Int
        private val lastLine: Int

        constructor(syncBuffer: SyncBuffer, bufferLinesEvent: BufLinesEvent) {
            if (bufferLinesEvent.hasMore) {
                TODO("Handle more")
            }
            this.syncBuffer = syncBuffer
            tick = bufferLinesEvent.changedTick
            lines = bufferLinesEvent.lineData
            firstLine = bufferLinesEvent.firstLine
            lastLine = bufferLinesEvent.lastLine
        }

        constructor(syncBuffer: SyncBuffer, changedtickEvent: BufChangedtickEvent) {
            this.syncBuffer = syncBuffer
            tick = changedtickEvent.changedTick
            lines = null
            firstLine = -1
            lastLine = -1
        }

        fun build(): BufferChange {
            return BufferChange(syncBuffer, firstLine, lastLine, lines, Source.NEOVIM, tick)
        }
    }

    class JetBrainsChangeBuilder(private val syncBuffer: SyncBuffer) {
        private lateinit var beforeChangeEvent: DocumentEvent
        private lateinit var afterChangeEvent: DocumentEvent
        private var startLine = 0
        private var endLine = 0
        private lateinit var lines: List<String>

        fun beforeChange(event: DocumentEvent): JetBrainsChangeBuilder {
            beforeChangeEvent = event
            startLine = event.document.getLineNumber(event.offset)
            endLine = event.document.getLineNumber(event.offset + event.oldLength) + 1
            return this
        }

        fun afterChange(event: DocumentEvent): JetBrainsChangeBuilder {
            afterChangeEvent = event
            val doc = event.document
            val afterEndLine = doc.getLineNumber(event.offset + event.newLength)
            lines = doc.getText(TextRange(doc.getLineStartOffset(startLine), doc.getLineEndOffset(afterEndLine)))
                    .split('\n')
            return this
        }

        fun build(): BufferChange {
            return BufferChange(syncBuffer, startLine, endLine, lines, Source.JetBrain,
                    syncBuffer.synchronizer.changedtick + 1)
        }
    }
}