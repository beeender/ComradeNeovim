import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.beeender.neovim.Client
import org.beeender.neovim.NeovimConnection
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.annotation.RequestHandler
import org.beeender.neovim.rpc.Notification
import org.beeender.neovim.rpc.Request
import org.beeender.neovim.rpc.Response
import org.junit.Test

class ClientTest {
    @Test
    fun registerHandler_Notification() {
        val conn = mockk<NeovimConnection>(relaxed = true)
        val handler = mockk<NotiHandler>(relaxed = true)

        val client = Client(conn)
        client.registerHandler(handler)

        client.receiver.msgHandler(Notification("TestNotification", listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }

        client.receiver.msgHandler(Response(1, null, listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }

        client.receiver.msgHandler(Request("TestNotification", listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }

        client.receiver.msgHandler(Notification("NotHandled", listOf(1, "a")))
        verify(exactly = 1) { handler.handleNotification(any()) }
    }

    @Test
    fun registerHandler_Request() {
        val conn = mockk<NeovimConnection>(relaxed = true)
        val handler = spyk<ReqHandler>()

        val client = Client(conn)
        client.registerHandler(handler)

        client.receiver.msgHandler(Request("TestRequest", listOf(1, "a")))
        verify(exactly = 1) { handler.handleRequest(any()) }

        client.receiver.msgHandler(Notification("TestRequest", listOf(1, "a")))
        verify(exactly = 1) { handler.handleRequest(any()) }

        client.receiver.msgHandler(Response(1, null, listOf(1, "a")))
        verify(exactly = 1) { handler.handleRequest(any()) }

        client.receiver.msgHandler(Request("NotHandled", listOf(1, "a")))
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