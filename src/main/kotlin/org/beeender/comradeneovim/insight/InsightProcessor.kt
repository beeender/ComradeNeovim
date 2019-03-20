package org.beeender.comradeneovim.insight

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.beeender.comradeneovim.ComradeNeovimPlugin
import org.beeender.comradeneovim.ComradeScope
import org.beeender.comradeneovim.core.*

private const val PROCESS_INTERVAL = 500L

object InsightProcessor : SyncBufferManagerListener {
    private val busConnection =
            ApplicationManager.getApplication().messageBus.connect(ComradeNeovimPlugin.instance)
    private var job: Deferred<Unit>? = null
    private var isStarted: Boolean = false

    fun start() {
        if (!isStarted) {
            busConnection.subscribe(SyncBufferManager.TOPIC, this)
            isStarted = true
        }
    }

    /**
     * Process the insight information immediately.
     */
    fun process(buffer: SyncBuffer) {
        val nvimInstance = buffer.nvimInstance
        ApplicationManager.getApplication().invokeLater {
            if (buffer.isReleased()) return@invokeLater

            val list = mutableListOf<HighlightInfo>()
            DaemonCodeAnalyzerEx.processHighlights(buffer.document,
                    buffer.project,
                    HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
                    0,
                    buffer.document.getLineEndOffset(buffer.document.lineCount - 1)) {
                info ->
                list.add(info)
                true
            }

            val insights = createInsights(buffer, list)
            ComradeScope.launch {
                nvimInstance.client.api.callFunction(FUN_SET_INSIGHT, listOf(buffer.id, insights))
            }
        }
    }

    private fun createInsights(buffer: SyncBuffer, infos: List<HighlightInfo>) : Map<Int, List<Map<String, Any>>>
    {
        val ret = mutableMapOf<Int, List<Map<String, Any>>>()
        infos.forEach {
            val insight = InsightItem(buffer, it).toMap()
            val startLine = insight["s_line"] as Int
            if (!ret.containsKey(startLine)) {
                ret[startLine] = mutableListOf()
            }
            val insightList = ret[startLine] as MutableList
            insightList.add(insight)
        }
        ret.values.forEach {
            (it as MutableList).sortByDescending { insightMap -> insightMap["severity"] as Int }
        }
        return ret
    }

    /**
     * Subscribe to the [SyncBufferManagerListener.bufferSynced] to trigger the insight information processing.
     */
    override fun bufferSynced(syncBuffer: SyncBuffer) {
        job?.cancel()
        job = ComradeScope.async {
            delay(PROCESS_INTERVAL)
            process(syncBuffer)
        }
    }
}