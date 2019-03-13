package org.beeender.comradeneovim.insight

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.beeender.comradeneovim.ComradeScope
import org.beeender.comradeneovim.core.NvimInstance
import org.beeender.comradeneovim.core.SyncedBuffer

private const val PROCESS_INTERVAL = 1000L

class InsightProcessor(private val nvimInstance: NvimInstance, private val buffer: SyncedBuffer) {
    private var job: Deferred<Unit>? = null

    fun process() {
        job?.cancel()
        job = ComradeScope.async {
            delay(PROCESS_INTERVAL)

            ApplicationManager.getApplication().invokeLater {
                if (buffer.isClosed()) return@invokeLater

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
                    nvimInstance.client.api.callFunction("comrade#SetInsights", listOf(buffer.id, insights))
                }
            }

        }
    }

    private fun createInsights(buffer: SyncedBuffer, infos: List<HighlightInfo>) : Map<Int, List<Map<String, Any>>>
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
}
