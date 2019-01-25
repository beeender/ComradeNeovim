package org.beeender.neovim.annotation

@Target(AnnotationTarget.FUNCTION)
annotation class RequestHandler(val name: String)