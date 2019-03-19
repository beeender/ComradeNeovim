package org.beeender.neovim

import org.beeender.neovim.annotation.MessageConverterFun
import org.beeender.neovim.rpc.Notification

class BufLinesEvent(val id: Int, val changedTick: Int, val firstLine: Int, val lastLine: Int, val lineData: List<String>,
                    val hasMore: Boolean) {
    @Suppress("UNCHECKED_CAST")
    constructor(notification: Notification) :
            this(
                    BufferApi.decodeBufId(notification),
                    notification.args[1] as Int,
                    notification.args[2] as Int,
                    notification.args[3] as Int,
                    notification.args[4] as List<String>,
                    notification.args[5] as Boolean
            )
}

class BufChangedtickEvent(val id: Int, val changedTick: Int) {
    constructor(notification: Notification) :
            this (BufferApi.decodeBufId(notification), notification.args[1] as Int)
}

class BufDetachEvent(val id: Int) {
    companion object {
        @MessageConverterFun
        fun fromNotification(notification: Notification) : BufDetachEvent {
            val bufId = BufferApi.decodeBufId(notification)
            return BufDetachEvent(bufId)
        }
    }
}
