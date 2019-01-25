package org.beeender.neovim

import com.sun.org.apache.xpath.internal.operations.Bool
import org.msgpack.core.MessagePack
import org.msgpack.jackson.dataformat.MessagePackExtensionType

class BufLinesEvent(val id: Int, val changedTick: Int, val firstLine: Int, val lastLine: Int, val lineData: List<String>,
                    val hasMore: Boolean) {
    @Suppress("UNCHECKED_CAST")
    constructor(args: ArrayList<Any?>) :
            this(
                    MessagePack.newDefaultUnpacker((args[0] as MessagePackExtensionType).data).unpackInt(),
                    args[1] as Int,
                    args[2] as Int,
                    args[3] as Int,
                    args[4] as List<String>,
                    args[5] as Boolean
            )
}