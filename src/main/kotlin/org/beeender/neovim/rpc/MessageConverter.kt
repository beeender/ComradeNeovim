package org.beeender.neovim.rpc

import org.beeender.neovim.annotation.MessageConverterFun
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

/**
 * Convert [Message] to specific classes. See [MessageConverterFun] for details.
 */
object MessageConverter {
    private val funMap = ConcurrentHashMap<KClass<*>, (Message) -> Any> ()

    fun registerConverterFun(clazz: KClass<*>) {
        if (funMap.contains(clazz)) return

        val companionInstance = clazz.companionObjectInstance ?: throw createException(clazz)
        for (function in companionInstance::class.memberFunctions) {
            if (function.findAnnotation<MessageConverterFun>() != null) {
                funMap[clazz] = {
                    function.call(companionInstance, it) as Any
                }
                return
            }
        }

        throw createException(clazz)
    }

    fun convert(clazz: KClass<*>, message: Message) : Any {
        val convertFun = funMap[clazz] ?: throw createException(clazz)

        return convertFun.invoke(message)
    }
}

private fun createException(clazz: KClass<*>) : Exception {
    return IllegalArgumentException(
            "A companion function needs to be annotated with '@MessageConverterFun' in class '${clazz.simpleName}'")
}

