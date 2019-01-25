package org.beeender.neovim.rpc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.intellij.openapi.diagnostic.Logger
import org.beeender.neovim.NeovimConnection
import org.msgpack.jackson.dataformat.MessagePackFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Receiver(connection: NeovimConnection, internal val msgHandler: (Message) -> Unit) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val log = Logger.getInstance(Receiver::class.java)
    private val objectMapper = ObjectMapper(MessagePackFactory()).registerKotlinModule()

    init {
        executor.submit {
            while (true) {
                val node = objectMapper.readTree(connection.inputStream)
                if (node == null) {
                    log.info("The inputstream has been closed.")
                }

                log.info("Received message 1: $node")

                if (!node.isArray || !node[0].isInt) {
                    log.error { "Bad message: $node" }
                }
                val msgType = node[0].intValue()
                when (msgType) {
                    MessageType.REQUEST.value -> msgHandler(objectMapper.treeToValue<Request>(node))
                    MessageType.RESPONSE.value -> msgHandler(objectMapper.treeToValue<Response>(node))
                    MessageType.NOTIFICATION.value -> msgHandler(objectMapper.treeToValue<Notification>(node))
                }
                log.info("Received message END")
            }
        }
    }
}