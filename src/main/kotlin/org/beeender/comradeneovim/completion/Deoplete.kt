package org.beeender.comradeneovim.completion

fun createCandidate(word: String) : Map<String, String>{
    return mapOf("word" to word)
}
