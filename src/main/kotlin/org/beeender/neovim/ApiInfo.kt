package org.beeender.neovim

class ApiInfo(val list: List<*>) {
    private val map: Map<*, *> by lazy {
        list.last() as Map<*, *>
    }
    val channelId: Int by lazy {list.first() as Int}
}