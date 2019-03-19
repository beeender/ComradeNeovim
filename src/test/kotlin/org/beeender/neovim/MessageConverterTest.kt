package org.beeender.neovim

import org.beeender.neovim.annotation.MessageConverterFun
import org.beeender.neovim.rpc.MessageConverter
import org.beeender.neovim.rpc.Notification
import org.junit.Test
import kotlin.test.assertEquals


class MessageConverterTest {
    @Test
    fun convert() {
        MessageConverter.registerConverterFun(Foo::class)
        val notification = Notification("foo", listOf(42))
        val foo = MessageConverter.convert(Foo::class, notification) as Foo
        assertEquals(foo.bar, 42)
    }
}

class Foo(val bar: Int) {
    companion object {
        @MessageConverterFun
        fun fromMessage(notification: Notification) : Foo {
            return Foo(notification.args[0] as Int)
        }
    }
}