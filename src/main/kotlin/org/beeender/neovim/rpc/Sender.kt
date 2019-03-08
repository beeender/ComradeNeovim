package org.beeender.neovim.rpc

import com.intellij.openapi.diagnostic.Logger
import org.beeender.neovim.NeovimConnection

class Sender(private val connection: NeovimConnection) {

    private val log = Logger.getInstance(Sender::class.java)

    fun send(msg: Message) {
        MsgPackMapper.writeValue(connection.outputStream, msg)
        log.debug("Connection['$connection'] Sent message: $msg")
    }
}