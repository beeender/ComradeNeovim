package org.beeender.comradeneovim.insight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.SuppressIntentionActionFromFix
import com.intellij.openapi.project.DumbService
import com.intellij.util.ThreeState
import org.beeender.comradeneovim.buffer.SyncBuffer

class InsightItem(private val syncBuffer: SyncBuffer, val highlightInfo: HighlightInfo) {
    val startLine: Int
    val endLine: Int
    val startColumn: Int
    val endColumn: Int
    val id: Int
    val actionList: List<HighlightInfo.IntentionActionDescriptor>

    init {
        val document = syncBuffer.document
        startLine = document.getLineNumber(highlightInfo.startOffset)
        endLine = document.getLineNumber(highlightInfo.endOffset)
        startColumn = highlightInfo.startOffset - document.getLineStartOffset(startLine)
        endColumn = highlightInfo.endOffset - document.getLineStartOffset(endLine)
        actionList = getAvailableFixes(highlightInfo)
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
        id = id * 31 + actionList.hashCode()
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
                "id" to id,
                "fixers" to actionList.map { it.action.text }
        )
    }

    /**
     * Check if an [IntentionAction] can be applied as a fix. Most logic is referred from
     * ShowIntentionsPass.addAvailableFixesForGroups().
     */
    private fun getAvailableFixes(info: HighlightInfo) : List<HighlightInfo.IntentionActionDescriptor> {
        val file = syncBuffer.psiFile
        val editorToUse = syncBuffer.editor
        val project = syncBuffer.project

        if (info.quickFixActionMarkers == null) return emptyList()
        val actionMarkers = info.quickFixActionMarkers ?: return emptyList()
        return actionMarkers.asSequence().filter { pair ->
            val actionDescriptor = pair.first
            var a = actionDescriptor.action
            while (a is IntentionActionDelegate) {
                a = a.delegate
            }
            val range = pair.second
            if (a is LowPriorityAction || (a is PriorityAction && a.priority == PriorityAction.Priority.LOW)) {
                //e.g.: EnableOptimizeImportsOnTheFlyFix . Those fixes are not quite necessary and may create problems.
                false
            } else if (!a.startInWriteAction()) {
                // It seems when this is false, the fixer needs to open a dialog to ask for user's input to do the next
                // step of fixing. That is not supported now.
                false
            } else if (!range.isValid) {
                false
            } else if (DumbService.isDumb(file.project) && !DumbService.isDumbAware(actionDescriptor.action)) {
                false
            } else actionDescriptor.action.isAvailable(project, editorToUse, file)
        }.map {
            it.first
        }.sortedByDescending {
            getWeight(it)
        }.toList()
    }
}

/**
 * To decide how important/convenient this fix is. So we can sort the list to put the most frequent fix on top.
 * eg.: The "import" fix should be on top.
 *
 * Copied from CacheIntentions.java with modifications.
 */
private fun getWeight(action : HighlightInfo.IntentionActionDescriptor) : Int {
    var a = action.action
    var weight = 0

    while (a is IntentionActionDelegate) {
        a = a.delegate
    }

    if (a is PriorityAction) {
        weight = getPriorityWeight(a.priority)
    } else if (a is SuppressIntentionActionFromFix) {
        if (a.isShouldBeAppliedToInjectionHost == ThreeState.NO) {
            weight =-1
        }
    }
    return weight
}

private fun getPriorityWeight(priority: PriorityAction.Priority) : Int{
    return when(priority) {
        PriorityAction.Priority.TOP -> 666
        PriorityAction.Priority.HIGH-> 3
        PriorityAction.Priority.LOW -> -33
        else -> 0
    }
}
