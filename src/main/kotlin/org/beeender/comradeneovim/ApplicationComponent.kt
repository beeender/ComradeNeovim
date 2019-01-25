package org.beeender.comradeneovim

import com.intellij.openapi.components.ApplicationComponent
import org.beeender.comradeneovim.core.autoConnect

class ApplicationComponent : ApplicationComponent {
    override fun initComponent() {
        autoConnect()
    }
}