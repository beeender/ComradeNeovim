package org.beeender.neovim.rpc

import com.fasterxml.jackson.module.kotlin.treeToValue
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.diagnostic.Logger
import org.beeender.neovim.NeovimConnection
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val RECEIVER_THREAD_FACTORY = ThreadFactoryBuilder().setNameFormat("ComradeNeovim-Receiver-%d").build()

class Receiver(private val connection: NeovimConnection) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(RECEIVER_THREAD_FACTORY)
    private val log = Logger.getInstance(Receiver::class.java)

    fun start(onReceive: (Message) -> Unit, onStop: (Throwable?) -> Unit) {
        executor.submit {
            log.info("The receiver for connection '$connection' has been started.")
            while (!Thread.interrupted()) {
                try {
                    val node = MsgPackMapper.readTree(connection.inputStream)
                    if (node == null) {
                        Thread.currentThread().interrupt()
                        onStop(null)
                        continue
                    }

                    log.debug("Received raw message: $node")

                    if (!node.isArray || !node[0].isInt) {
                        log.warn("Bad message: $node")
                    }
                    val msgType = node[0].intValue()
                    val msg = when (MessageType.valueOf(msgType)) {
                        MessageType.REQUEST -> MsgPackMapper.treeToValue<Request>(node)
                        MessageType.RESPONSE -> MsgPackMapper.treeToValue<Response>(node)
                        MessageType.NOTIFICATION -> MsgPackMapper.treeToValue<Notification>(node)
                        else -> throw IllegalArgumentException()
                    }
                    log.debug("Received message: $msg")
                    onReceive(msg)
                }
                catch (t: Throwable) {
                    Thread.currentThread().interrupt()
                    onStop(t)
                }
            }
            log.debug("The receiver for connection '$connection' has been stopped.")
        }
    }
}