package org.beeender.neovim

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.*
import org.beeender.neovim.annotation.NotificationHandler
import org.beeender.neovim.annotation.RequestHandler
import org.beeender.neovim.rpc.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

class Client(connection: NeovimConnection, onClose: (Throwable?) -> Unit) {
    companion object {
        private val SenderChannel = Channel<Pair<Client, Message>>(Channel.UNLIMITED)
        private val ReceiverChannel = Channel<Pair<Client, Message>>(Channel.UNLIMITED)
        private val nvimClientScope = CoroutineScope(newSingleThreadContext("NeovimClient"))

        init {
            nvimClientScope.launch {
                while (true) {
                    select<Unit> {
                        ReceiverChannel.onReceive{ pair ->
                            pair.first.handleMessage(pair.second)
                        }
                        SenderChannel.onReceive { pair ->
                            pair.first.sender.send(pair.second)
                        }
                    }
                }
            }
        }
    }

    private val receiver = Receiver(connection)
    private val sender = Sender(connection)

    private val resHandlers = ConcurrentHashMap<Long, ((Response) -> Unit)>()
    private val reqHandlers = ConcurrentHashMap<String, ((Request) -> Response)>()
    private val notiHandlers = ConcurrentHashMap<String, ((Notification) -> Unit)>()
    private val log = Logger.getInstance(Client::class.java)

    val api = Api(this)
    val bufferApi = BufferApi(this)

    init {
        receiver.start({msg ->
            ReceiverChannel.offer(this to msg)
        }, onClose)
    }

    suspend fun request(method: String, args: List<Any?>) : Response {
        val req = Request(method, args)
        val ret = AtomicReference<Response>()
        val channel = Channel<Response>()
        val handler: (Response) -> Unit = { rsp: Response ->
            ret.set(rsp)
            channel.offer(rsp)
        }
        resHandlers[req.id] = handler
        SenderChannel.offer(this to req)
        return channel.receive()
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
                log.warn("'$msg' is not handled.")
            }
        }
        else if (msg is Request) {
            val handler = reqHandlers[msg.method]
            val rsp: Response = when (handler) {
                null -> Response(msg, "There is no request handler registered for ${msg.method}", null)
                else ->
                    try {
                        handler.invoke(msg)
                    } catch (t : Throwable) {
                        Response(msg, t.toString(), null)
                    }
            }
            SenderChannel.offer(this to rsp)
        }
        else if (msg is Notification) {
            if (notiHandlers.containsKey(msg.name)) {
                val handler = notiHandlers[msg.name]
                if (handler != null) handler(msg)
            }
        }
    }
}