package org.beeender.comradeneovim

import com.intellij.openapi.components.ApplicationComponent
import org.beeender.comradeneovim.core.NvimInstanceManager

class ApplicationComponent : ApplicationComponent {
    override fun initComponent() {
        NvimInstanceManager.start()
        NvimInstanceManager.refresh()
    }

    override fun disposeComponent() {
        NvimInstanceManager.stop()
        super.disposeComponent()
    }
}