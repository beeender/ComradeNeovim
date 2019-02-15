package org.beeender.neovim

import org.msgpack.core.MessagePack
import org.msgpack.jackson.dataformat.MessagePackExtensionType

class Api internal constructor(private val client: Client) {
    suspend fun callFunction(name: String, args: List<String>) : Any? {
        val rsp = client.request("nvim_call_function", listOf(name, args))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
        return rsp.result
    }

    suspend fun setVar(name: String, value: Any?) {
        val rsp = client.request("nvim_set_var", listOf(name, value))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
    }

    suspend fun command(cmdLine: String) {
        val rsp = client.request("nvim_command", listOf(cmdLine))
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
    }

    suspend fun getApiInfo() : ApiInfo {
        val rsp = client.request("nvim_get_api_info", emptyList())
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
        return ApiInfo(rsp.result as List<*>)
    }

    suspend fun getCurrentBuf() : Int {
        val rsp = client.request("nvim_get_current_buf", emptyList())
        if (rsp.error != null) {
            throw Exception(rsp.error.toString())
        }
        return MessagePack.newDefaultUnpacker((rsp.result as MessagePackExtensionType).data).unpackInt()
    }
}
