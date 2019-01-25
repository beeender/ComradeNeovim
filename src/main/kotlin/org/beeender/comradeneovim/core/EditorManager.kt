package org.beeender.comradeneovim.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import java.io.File

fun getVirtualFile(path: String) : VirtualFile?
{
    return ApplicationManager.getApplication().runWriteAction<VirtualFile?> {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(path))
    }
}

fun getEditor(path: String, content: String) : Editor?
{
    val virtualFile: VirtualFile? = getVirtualFile(path) ?: return null
    val project = ProjectLocator.getInstance().guessProjectForFile(virtualFile) ?: return null
    val orgPsiFile = PsiManager.getInstance(project).findFile(virtualFile!!) ?: return null

    val psiFactory = PsiFileFactory.getInstance(project) ?: return null
    val newPsiFile = psiFactory.createFileFromText(content, orgPsiFile) ?: return null
    val newDocument = newPsiFile.viewProvider.document ?: return null
    val editorFactory = EditorFactory.getInstance()
    return editorFactory.createEditor(newDocument)
}

class EditorManager {


}