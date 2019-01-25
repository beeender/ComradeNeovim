package org.beeender.neovim.rpc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.diagnostic.Logger
import org.beeender.neovim.NeovimConnection
import org.msgpack.jackson.dataformat.MessagePackFactory

class Sender(private val connection: NeovimConnection) {

    private val objectMapper = ObjectMapper(MessagePackFactory()).registerKotlinModule()
    private val log = Logger.getInstance(Receiver::class.java)

    fun send(msg: Message) {
        objectMapper.writeValue(connection.outputStream, msg)
        log.info(msg.toString())
    }
}