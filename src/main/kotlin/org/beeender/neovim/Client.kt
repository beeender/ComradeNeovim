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
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

private val log = Logger.getInstance(Client::class.java)

class Client(connection: NeovimConnection, onClose: (Throwable?) -> Unit) {
    companion object {
        internal val SenderChannel = Channel<Pair<Client, Message>>(Channel.UNLIMITED)
        internal val ReceiverChannel = Channel<Pair<Client, Message>>(Channel.UNLIMITED)
        private val nvimClientScope = CoroutineScope(newSingleThreadContext("NeovimClient"))

        init {
            nvimClientScope.launch {
                try {
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
                } catch (t: Throwable) {
                    log.warn("Neovim client loop finished", t)
                }
            }
        }
    }

    private val receiver = Receiver(connection)
    private val sender = Sender(connection)

    private val resHandlers = ConcurrentHashMap<Long, ((Response) -> Unit)>()
    private val reqHandlers = ConcurrentHashMap<String, ((Request) -> Any?)>()
    private val notiHandlers = ConcurrentHashMap<String, ((Notification) -> Unit)>()

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
                registerConvert(function)
                if (reqHandlers.containsKey(reqAnnotation.name)) {
                    log.warn("Request Handler for ${reqAnnotation.name} has been registered already.")
                }
                reqHandlers[reqAnnotation.name] = {
                    function.call(obj, convertParam(function.parameters[1].type, it))
                }
            }
            val notiAnnotation = function.findAnnotation<NotificationHandler>()
            if (notiAnnotation != null) {
                registerConvert(function)
                if (notiHandlers.containsKey(notiAnnotation.name)) {
                    log.warn("Notification Handler for ${notiAnnotation.name} has been registered already.")
                }
                notiHandlers[notiAnnotation.name] = {
                    function.call(obj, convertParam(function.parameters[1].type, it))
                }
            }
        }
    }

    private fun registerConvert(function: KFunction<*>) {
        if (function.parameters.size != 2) {
            throw IllegalArgumentException("Message handler function should take one and only one parameter. $function")
        }
        MessageConverter.registerConverterFun(function.parameters[1].type.classifier as KClass<*>)
    }

    private fun convertParam(ktype: KType, message: Message) : Any {
        return MessageConverter.convert(ktype.classifier as KClass<*>, message)
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
                        val rspArgs = handler.invoke(msg)
                        Response(msg, null, rspArgs)
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