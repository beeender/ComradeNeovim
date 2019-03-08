package org.beeender.neovim

import kotlinx.coroutines.withTimeoutOrNull
import org.beeender.neovim.rpc.Notification
import org.msgpack.core.MessagePack
import org.msgpack.jackson.dataformat.MessagePackExtensionType
import java.lang.IllegalArgumentException

class BufferApi internal constructor(private val client: Client) {
    suspend fun attach(id: Int, sendBuf: Boolean) {
        // To work around some nvim remote API bugs. See https://github.com/neovim/neovim/issues/8634 .
        // nvim doesn't send the response occasionally.
        val rsp = withTimeoutOrNull(100) {
            client.request("nvim_buf_attach", listOf(id, sendBuf, emptyMap<Any?, Any?>()))
        }
        if (rsp?.error != null) {
            throw Exception(rsp.error.toString())
        }
    }

    suspend fun detach(id: Int) {
        val rsp = withTimeoutOrNull(100) {
            client.request("nvim_buf_detach", listOf(id))
        }
        if (rsp?.error != null) {
            throw Exception(rsp.error.toString())
        }
    }

    suspend fun getName(id: Int) : String {
        val rsp = client.request("nvim_buf_get_name", listOf(id))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
        return rsp.result as String
    }

    suspend fun getLines(id: Int, start: Int, end: Int, strictIndexing: Boolean) : List<String>
    {
        val rsp = client.request("nvim_buf_get_lines", listOf(id, start, end, strictIndexing))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return rsp.result as List<String>
    }

    companion object {
        fun decodeBufId(obj: Any?) : Int {
            if (obj is Notification) {
                return MessagePack.newDefaultUnpacker((obj.args[0] as MessagePackExtensionType).data).unpackInt()
            }
            else if (obj is MessagePackExtensionType) {
                return MessagePack.newDefaultUnpacker(obj.data).unpackInt()
            }
            throw IllegalArgumentException("The given object cannot be decoded as a Buffer ID. \n $obj")
        }
    }
}
