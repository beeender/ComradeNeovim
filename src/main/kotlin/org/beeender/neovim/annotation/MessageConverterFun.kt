package org.beeender.neovim.annotation

import org.beeender.neovim.rpc.*

/**
 * When register message handlers, instead of taking [Notification]/[Request] as the input parameter, a specific class
 * can be used as the input parameter. Just add a companion method in the class and annotate it as [MessageConverterFun].
 * The neovim client will automatically convert the [Message] to the class instance when needed.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class MessageConverterFun
