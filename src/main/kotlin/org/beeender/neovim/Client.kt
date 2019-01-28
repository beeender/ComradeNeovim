package org.beeender.neovim

import com.intellij.openapi.diagnostic.Logger
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.annotation.RequestHandler
import org.beeender.neovim.rpc.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

class Client(connection: NeovimConnection, onClose: (Throwable?) -> Unit) {
    internal val receiver = Receiver(connection)
    private val sender = Sender(connection)

    private val resHandlers = ConcurrentHashMap<Long, ((Response) -> Unit)>()
    private val reqHandlers = ConcurrentHashMap<String, ((Request) -> Response)>()
    private val notiHandlers = ConcurrentHashMap<String, ((Notification) -> Unit)>()
    private val log = Logger.getInstance(Client::class.java)

    val api = Api(this)
    val bufferApi = BufferApi(this)

    init {
        receiver.start( {handleMessage(it)}, onClose)
    }

    fun request(method: String, args: List<Any?>) : Response {
        val req = Request(method, args)
        val ret = AtomicReference<Response>()
        val latch = CountDownLatch(1)
        val handler: (Response) -> Unit = { rsp: Response ->
            ret.set(rsp)
            latch.countDown()
        }
        resHandlers[req.id] = handler
        sender.send(req)
        latch.await()
        return ret.get()
    }

    // To work around some nvim remote API bugs. See https://github.com/neovim/neovim/issues/8634 .
    fun requestOnly(method: String, args: List<Any?>) {
        val req = Request(method, args)
        sender.send(req)
    }

    fun registerHandler(obj: Any) {
        for (function in obj::class.memberFunctions) {
            val reqAnnotation = function.findAnnotation<RequestHandler>()
            if (reqAnnotation != null) {
                if (reqHandlers.containsKey(reqAnnotation.name)) {
                    log.warn("Request Handler for ${reqAnnotation.name} has been registered already.")
                }
                reqHandlers[reqAnnotation.name] = {
                    function.call(obj, it) as Response
                }
            }
            val notiAnnotation = function.findAnnotation<NotificationHandler>()
            if (notiAnnotation != null) {
                if (notiHandlers.containsKey(notiAnnotation.name)) {
                    log.warn("Notification Handler for ${notiAnnotation.name} has been registered already.")
                }
                notiHandlers[notiAnnotation.name] = {
                    function.call(obj, it)
                }
            }
        }
    }

    private fun handleMessage(msg: Message) {
        if (msg is Response) {
            if (resHandlers.containsKey(msg.id)) {
                val handler = resHandlers[msg.id]
                resHandlers.remove(msg.id)
                if (handler != null) handler(msg)
            } else {

            }
        }
        else if (msg is Request) {
            if (reqHandlers.containsKey(msg.method)) {
                val handler = reqHandlers[msg.method]
                val rsp = if (handler != null) handler(msg) else Response(msg, "error", null)
                sender.send(rsp)
            }
        }
        else if (msg is Notification) {
            if (notiHandlers.containsKey(msg.name)) {
                val handler = notiHandlers[msg.name]
                if (handler != null) handler(msg)
            }
        }
    }
}