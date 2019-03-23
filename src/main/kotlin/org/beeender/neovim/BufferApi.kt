package org.beeender.neovim

import org.beeender.neovim.rpc.Notification
import org.msgpack.core.MessagePack
import org.msgpack.jackson.dataformat.MessagePackExtensionType
import java.lang.IllegalArgumentException

class BufferApi internal constructor(private val client: Client) {
    suspend fun attach(id: Int, sendBuf: Boolean) {
        val rsp = client.request(Constants.FUN_NVIM_BUF_ATTACH, listOf(id, sendBuf, emptyMap<Any?, Any?>()))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
    }

    suspend fun detach(id: Int) {
        val rsp = client.request(Constants.FUN_NVIM_BUF_DETACH, listOf(id))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
    }

    suspend fun getName(id: Int) : String {
        val rsp = client.request(Constants.FUN_NVIM_BUF_GET_NAME, listOf(id))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
        return rsp.result as String
    }

    suspend fun getLines(id: Int, start: Int, end: Int, strictIndexing: Boolean) : List<String>
    {
        val rsp = client.request(Constants.FUN_NVIM_BUF_GET_LINES, listOf(id, start, end, strictIndexing))
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
