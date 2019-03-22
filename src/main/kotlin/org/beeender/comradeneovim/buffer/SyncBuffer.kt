package org.beeender.comradeneovim.buffer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.beeender.comradeneovim.core.NvimInstance
import java.io.File

class BufferNotInProjectException (bufId: Int, path: String, msg: String) :
        Exception("Buffer '$bufId' to '$path' cannot be found in any opened projects.\n$msg")

private val log = Logger.getInstance(SyncBuffer::class.java)

class SyncBuffer(val id: Int,
                 val path: String,
                 val nvimInstance: NvimInstance) {

    internal val psiFile: PsiFile
    internal val document: Document
    private var _editor: EditorDelegate? = null
    val editor: EditorDelegate
        get() {
            ApplicationManager.getApplication().assertIsDispatchThread()
            val backed = _editor
            if (backed == null || backed.isDisposed) {
                _editor = createEditorDelegate()
            }
            return _editor!!
        }

    val project: Project
    val text get() = document.text
    var isReleased: Boolean = false
        private set

    internal lateinit var synchronizer: Synchronizer

    private val fileEditorManager: FileEditorManager

    init {
        val pair = locateFile(path) ?:
            throw BufferNotInProjectException(id, path, "'locateFile' cannot locate the corresponding document.")
        project = pair.first
        psiFile = pair.second
        document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?:
                throw BufferNotInProjectException(id, path, "'PsiDocumentManager' cannot locate the corresponding document.")

        fileEditorManager = FileEditorManager.getInstance(project)
    }

    private fun createEditorDelegate(): EditorDelegate {
        val fileEditors = fileEditorManager.openFile(psiFile.virtualFile, false, true)
        val fileEditor = fileEditors.firstOrNull { it is TextEditor && it.editor is EditorEx } ?:
        throw BufferNotInProjectException(id, path, "FileEditorManger cannot open a TextEditor.")
        return EditorDelegate((fileEditor as TextEditor).editor as EditorEx)
    }


    /**
     * Navigate to the editor of the buffer in the IDE without requesting focus.
     * So ideally the contents in both IDE and nvim should be synced from time to time.
     */
    fun navigate() {
        val selectedFiles = fileEditorManager.selectedFiles
        if (selectedFiles.isEmpty() || selectedFiles.first() != psiFile.virtualFile) {
            OpenFileDescriptor(project, psiFile.virtualFile).navigate(false)
        }
    }

    fun getCaretOnPosition(row: Int, col: Int) : Caret {
        val caret = editor.editor.caretModel.currentCaret
        caret.moveToLogicalPosition(LogicalPosition(row, col))
        return caret
    }

    internal fun setText(text: CharSequence) {
        ApplicationManager.getApplication().runWriteAction {
            document.setText(text)
        }
    }

    internal fun replaceText(startOffset: Int, endOffset: Int, text: CharSequence) {
        log.info("replaceText start: $startOffset, end: $endOffset, with '$text'")
        ApplicationManager.getApplication().runWriteAction {
            WriteCommandAction.writeCommandAction(project)
                    .run<Throwable> {
                        document.replaceString(startOffset, endOffset, text)
                        log.info("replaceText")
                    }
        }
    }

    internal fun insertText(offset: Int, text: CharSequence) {
        ApplicationManager.getApplication().runWriteAction {
            WriteCommandAction.writeCommandAction(project)
                    .run<Throwable> {
                        document.insertString(offset, text)
                        log.info("insertText")
                    }
        }
    }

    internal fun deleteText(start: Int, end: Int) {
        ApplicationManager.getApplication().runWriteAction {
            WriteCommandAction.writeCommandAction(project)
                    .run<Throwable> {
                        document.deleteString(start, end)
                        log.info("deleteText")
                    }
        }
    }

    internal fun attachSynchronizer(synchronizer: Synchronizer) {
        this.synchronizer = synchronizer
        document.addDocumentListener(synchronizer)
        synchronizer.initFromJetBrain()
    }

    /**
     * Use [SyncBufferManager.releaseBuffer] to dispose the [SyncBuffer].
     */
    internal fun release() {
        isReleased = true
        document.removeDocumentListener(synchronizer)
    }
}

private fun locateFile(name: String) : Pair<Project, PsiFile>? {
    var ret: Pair<Project, PsiFile>? = null
		ApplicationManager.getApplication().runReadAction {
        val projectManager = ProjectManager.getInstance()
        val projects = projectManager.openProjects
        projects.forEach { project ->
            val files = com.intellij.psi.search.FilenameIndex.getFilesByName(
                    project, File(name).name, GlobalSearchScope.allScope(project))
            val psiFile = files.find { it.virtualFile.canonicalPath == name }
            if (psiFile != null) {
                ret = project to psiFile
                return@runReadAction
            }
        }
    }
    return ret
}