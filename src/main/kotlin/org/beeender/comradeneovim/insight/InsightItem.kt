package org.beeender.comradeneovim.insight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.beeender.comradeneovim.core.SyncedBuffer

class InsightItem(syncedBuffer: SyncedBuffer, val highlightInfo: HighlightInfo) {
    val startLine: Int
    val endLine: Int
    val startColumn: Int
    val endColumn: Int
    val id: Int

    init {
        val document = syncedBuffer.document
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
        id = id * 31 + highlightInfo.description.hashCode()
        if (id < 0) id = -id
        return id
    }

    fun toMap():Map<String, Any> {
        return mapOf(
                "s_line" to startLine + 1,
                "s_col"  to startColumn,
                "e_line" to endLine + 1,
                "e_col" to endColumn,
                "desc" to highlightInfo.description,
                "severity" to highlightInfo.severity.myVal,
                "id" to id
        )
    }
}
