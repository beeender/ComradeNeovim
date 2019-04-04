@file:Suppress("DEPRECATION")

package org.beeender.comradeneovim.completion

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement

internal class CodeCompletionHandler(private val callback: (lookupElements: List<LookupElement>) -> Unit) :
        CodeCompletionHandlerBase(CompletionType.BASIC, true, false, true) {
    @Suppress("DEPRECATION")
    override fun completionFinished(indicator: CompletionProgressIndicator?, hasModifiers: Boolean) {
        val items = indicator?.lookup?.items ?: return
        callback(items)
    }
}