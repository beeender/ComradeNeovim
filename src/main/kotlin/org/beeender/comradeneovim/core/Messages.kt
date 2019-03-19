package org.beeender.comradeneovim.core

import org.beeender.neovim.annotation.MessageConverterFun
import org.beeender.neovim.rpc.Notification
import org.beeender.neovim.rpc.Request

class ComradeBufEnterParams(val id: Int, val path: String) {
    companion object {
        @MessageConverterFun
        fun fromNotification(notification: Notification) : ComradeBufEnterParams {
            val map = notification.args.first() as Map<*, *>
            val id = map["id"] as Int
            val path =  map["path"] as String
            return ComradeBufEnterParams(id, path)
        }
    }
}

class ComradeBufWriteParams(val id: Int) {
    companion object {
        @MessageConverterFun
        fun fromMessage(request: Request) : ComradeBufWriteParams {
            val bufId = (request.args[0] as Map<*, *>)["id"] as Int
            return ComradeBufWriteParams(bufId)
        }
    }
}
