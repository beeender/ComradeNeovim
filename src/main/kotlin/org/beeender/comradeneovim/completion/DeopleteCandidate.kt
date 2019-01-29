package org.beeender.comradeneovim.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation

class Candidate : LookupElementPresentation() {
    fun toMap() : Map<String, String?>
    {
        return mapOf(
                "word" to itemText,
                "abbr" to itemText + tailText,
                "kind" to typeText)
    }
}
