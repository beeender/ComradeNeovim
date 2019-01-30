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

    @RequestHandler("comrade_complete")
    fun intellijComplete(req: Request) : Response {
        var candidates : List<Map<String, String>>? = null

        ApplicationManager.getApplication().invokeAndWait {
            candidates = complete(req)
        }

        return Response(req, null, candidates)
    }

    private fun complete(req: Request) : List<Map<String, String>> {
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

        return candidates
    }
}
