package org.beeender.neovim.rpc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.msgpack.jackson.dataformat.MessagePackFactory

internal val MsgPackMapper = ObjectMapper(MessagePackFactory())
        .registerKotlinModule()
        .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
        .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
