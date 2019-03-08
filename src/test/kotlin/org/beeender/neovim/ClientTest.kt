package org.beeender.neovim

import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.annotation.RequestHandler
import org.beeender.neovim.rpc.Message
import org.beeender.neovim.rpc.Notification
import org.beeender.neovim.rpc.Request
import org.beeender.neovim.rpc.Response
import org.junit.Before
import org.junit.Test

class ClientTest {
    private lateinit var conn: NeovimConnection
    private lateinit var client: Client

    @Before
    fun setUp() {
        conn = mockk(relaxed = true)
        client = Client(conn) {}
    }

    private fun sendMessage(msg: Message) {
        runBlocking {
            Client.ReceiverChannel.send(client to msg)
            while (!Client.ReceiverChannel.isEmpty) ;
        }
    }

    @Test
    fun registerHandler_Notification() {
        val handler = mockk<NotiHandler>(relaxed = true)

        client.registerHandler(handler)

        sendMessage(Notification("TestNotification", listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }

        sendMessage(Response(1, null, listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }

        sendMessage(Request("TestNotification", listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }

        sendMessage(Notification("NotHandled", listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }
    }

    @Test
    fun registerHandler_Request() {
        val handler = spyk<ReqHandler>()

        client.registerHandler(handler)

        sendMessage(Request("TestRequest", listOf(1, "a")))
        verify(exactly = 1) { handler.handleRequest(any()) }

        sendMessage(Notification("TestRequest", listOf(1, "a")))
        verify(exactly = 1) { handler.handleRequest(any()) }

        sendMessage(Response(1, null, listOf(1, "a")))
        verify(exactly = 1) { handler.handleRequest(any()) }

        sendMessage(Request("NotHandled", listOf(1, "a")))
        verify(exactly = 1) { handler.handleRequest(any()) }
    }


    class NotiHandler {
        @Suppress("UNUSED_PARAMETER")
        @NotificationHandler("TestNotification")
        fun handleNotification(notification: Notification) {
        }
    }

    class ReqHandler {
        @RequestHandler("TestRequest")
        fun handleRequest(request: Request) : Response {
            return Response(request, null, listOf(2, "b"))
        }
    }
}