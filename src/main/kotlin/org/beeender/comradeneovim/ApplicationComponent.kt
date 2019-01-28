package org.beeender.comradeneovim

import com.intellij.openapi.components.ApplicationComponent
import org.beeender.comradeneovim.core.NvimInstanceManager
import org.beeender.comradeneovim.core.NvimInstanceWatcher
import org.beeender.comradeneovim.core.autoConnect

class ApplicationComponent : ApplicationComponent {
    override fun initComponent() {
        NvimInstanceManager.start()
    }

    override fun disposeComponent() {
        NvimInstanceManager.stop()
        super.disposeComponent()
    }
}