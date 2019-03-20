package org.beeender.comradeneovim.insight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.beeender.comradeneovim.buffer.SyncBuffer

class InsightItem(syncBuffer: SyncBuffer, val highlightInfo: HighlightInfo) {
    val startLine: Int
    val endLine: Int
    val startColumn: Int
    val endColumn: Int
    val id: Int

    init {
        val document = syncBuffer.document
        startLine = document.getLineNumber(highlightInfo.startOffset)
        endLine = document.getLineNumber(highlightInfo.endOffset)
        startColumn = highlightInfo.startOffset - document.getLineStartOffset(startLine)
        endColumn = highlightInfo.endOffset - document.getLineStartOffset(endLine)
        id = computeId()
    }

    /**
     * Create a unique ID for InsightItem. This ID can be used as the sign name in vim sign column.
     */
    private fun computeId(): Int {
        var id= highlightInfo.severity.hashCode() * 31
        id = id * 31 + highlightInfo.startOffset
        id = id * 31 + highlightInfo.endOffset
        id = id * 31 + if (highlightInfo.description == null) 0 else highlightInfo.description.hashCode()
        if (id < 0) id = -id
        return id
    }

    override fun hashCode(): Int {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InsightItem

        if (highlightInfo != other.highlightInfo) return false
        if (startLine != other.startLine) return false
        if (endLine != other.endLine) return false
        if (startColumn != other.startColumn) return false
        if (endColumn != other.endColumn) return false
        if (id != other.id) return false

        return true
    }

    fun toMap():Map<String, Any> {
        return mapOf(
                "s_line" to startLine,
                "s_col"  to startColumn,
                "e_line" to endLine,
                "e_col" to endColumn,
                "desc" to highlightInfo.description,
                "severity" to highlightInfo.severity.myVal,
                "id" to id
        )
    }
}