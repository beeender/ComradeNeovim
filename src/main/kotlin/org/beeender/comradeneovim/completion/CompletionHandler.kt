package org.beeender.comradeneovim.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import org.beeender.comradeneovim.core.SyncedBufferManager
import org.beeender.neovim.annotation.RequestHandler
import org.beeender.neovim.rpc.Request
import org.beeender.neovim.rpc.Response

class CompletionHandler(private val bufManager: SyncedBufferManager) {
    private class Results(args: Map<*, *>) {
        companion object {
            fun computeId(args: Map<*, *>) : Int {
                return args["buf_id"] as Int + args["buf_changedtick"] as Int + args["row"] as Int + args["col"] as Int
            }
        }

        @Volatile
        var candidates: List<Map<String, String>>? = null
        val id: Int

        init {
            id = computeId(args)
        }
    }

    private companion object {
        fun createCompletionResults(isFinished: Boolean, candidates: List<Map<String, String>>?) : Map<*, *> {
            return mapOf(
                    "is_finished" to isFinished,
                    "candidates" to (candidates ?: emptyList()) )
        }
    }

    @Volatile
    private var results: Results? = null

    @RequestHandler("comrade_complete")
    fun intellijComplete(req: Request) : Response {
        val map = req.args.first() as Map<*, *>
        val currentResultsId = Results.computeId(map)
        if (results?.id == currentResultsId) {
            val candidates = results?.candidates
            return Response(req, null, createCompletionResults(candidates != null, candidates))
        } else {
            val curRes =  Results(map)
            results = curRes
            ApplicationManager.getApplication().invokeLater {
                if (results?.id == currentResultsId)
                    doComplete(req, curRes)
            }
        }

        return Response(req, null, createCompletionResults(false, null))
    }

    private fun doComplete(req: Request, results: Results) {
        val candidates = mutableListOf<Map<String, String>>()
        val map = req.args.first() as Map<*, *>
        val bufName = map["buf_name"] as String
        val row = map["row"] as Int
        val col = map["col"] as Int

        val syncedBuf = bufManager.findBufferByPath(bufName) ?: throw IllegalStateException()
        val project = syncedBuf.project
        val caret = syncedBuf.getCaretOnPosition(row, col)
        val editor = syncedBuf.editor

        val completionService = CompletionServiceImpl.getCompletionService()
        val completionParams = completionService.createCompletionParameters(
                project, editor, caret, 1, CompletionType.BASIC,
                Disposable {
                })
        completionService.performCompletion(completionParams) {
            val result = it
            val lookupElement = result.lookupElement
            Candidate.addCandidate(candidates, lookupElement)
        }

        results.candidates = candidates
    }
}
