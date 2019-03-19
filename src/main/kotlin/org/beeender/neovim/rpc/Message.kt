package org.beeender.neovim.rpc

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonValue
import org.beeender.neovim.annotation.MessageConverterFun
import java.util.concurrent.atomic.AtomicLong

private val requestId = AtomicLong(0)

enum class MessageType constructor(@JsonValue val value: Int) {
    REQUEST(0),
    RESPONSE(1),
    NOTIFICATION(2);

    companion object {
        fun valueOf(value: Int) = MessageType.values().find { it.value == value }
    }
}

@Suppress("unused")
abstract class Message(val type: MessageType)

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("type", "id", "method", "args")
class Request(val id: Long, val method: String, val args: List<Any?>) : Message(MessageType.REQUEST)
{
    companion object {
        @MessageConverterFun
        fun noConvert(request: Request) : Request {
            return request
        }
    }

    constructor(method: String, args: List<Any?>) : this(requestId.getAndIncrement(), method, args)

    override fun toString(): String {
        return "Request Id: '$id', '$method', '$args'"
    }
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("type", "id", "error", "result")
class Response(val id: Long, val error: Any?, val result: Any?) : Message(MessageType.RESPONSE)
{
    companion object {
        @MessageConverterFun
        fun noConvert(response: Response) : Response {
            return response
        }
    }

    constructor(request: Request, error: Any?, result: Any?) : this(request.id, error, result)
    override fun toString(): String {
        return "Response Id: '$id', error: '$error', result: '$result'"
    }
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("type", "name", "args")
class Notification(val name: String, val args: List<Any?>) : Message(MessageType.NOTIFICATION) {
    companion object {
        @MessageConverterFun
        fun noConvert(notification: Notification) : Notification {
            return notification
        }
    }

    override fun toString(): String {
        return "Notification '$name', $args"
    }
}

