package org.beeender.comradeneovim.buffer

import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.externalSystem.service.execution.NotSupportedException
import javax.swing.JComponent

/**
 * When running some actions, we usually need to pass an editor instance to it since the action may need to have some
 * UI interactions during processing. eg.: A pop up list during multi choices import fixer. We don't support those right
 * now.
 */
@Suppress("CanBeParameter")
class NotSupportedByUIDelegateException(val editor: EditorEx, val methodName: String) :
        NotSupportedException("$methodName is not supported by EditorDelegate for ${editor.virtualFile.name}")

class EditorDelegate(val editor: EditorEx) : EditorEx by editor {

    override fun getComponent(): JComponent {
        throw NotSupportedByUIDelegateException(editor, "getComponent")
    }

    override fun getContentComponent(): JComponent {
        throw NotSupportedByUIDelegateException(editor, "getContentComponent")
    }

    override fun getCaretModel(): CaretModel {
        throw NotSupportedByUIDelegateException(editor, "getCaretModel")
    }
}