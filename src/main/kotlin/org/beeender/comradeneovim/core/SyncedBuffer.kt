package org.beeender.comradeneovim.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.beeender.neovim.BufLinesEvent
import java.io.Closeable
import java.io.File

class BufferNotInProjectException (bufId: Int, path: String, msg: String) :
        Exception("Buffer '$bufId' to '$path' cannot be found in any opened projects.\n$msg")

private val log = Logger.getInstance(SyncedBuffer::class.java)

class SyncedBuffer(val id: Int, val path: String) : Closeable {

    private val psiFile: PsiFile
    internal val document: Document
    val editor: Editor
    val project: Project
    private var changedTick: Int = -1
    val text get() = document.text
    // SyncedBufferManager will set this to true if the validation fails
    internal var detached = false
    private var changedByNvim = false

    private val listener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
        }
    }

    init {
        val pair = locateFile(path) ?:
            throw BufferNotInProjectException(id, path, "'locateFile' cannot locate the corresponding document.")
        project = pair.first
        psiFile = pair.second
        document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?:
                throw BufferNotInProjectException(id, path, "'PsiDocumentManager' cannot locate the corresponding document.")

        editor = EditorFactory.getInstance().createEditor(document)
        document.addDocumentListener(listener)
    }

    /**
     * Navigate to the editor of the buffer in the IDE without requesting focus.
     * So ideally the contents in both IDE and nvim should be synced from time to time.
     */
    fun navigate() {
        OpenFileDescriptor(project, psiFile.virtualFile).navigate(false)
    }

    fun getCaretOnPosition(row: Int, col: Int) : Caret {
        val caret = editor.caretModel.currentCaret
        caret.moveToLogicalPosition(LogicalPosition(row, col))
        return caret
    }

    private fun setText(text: CharSequence) {
        ApplicationManager.getApplication().runWriteAction {
            document.setText(text)
        }
    }

    private fun replaceText(startOffset: Int, endOffset: Int, text: CharSequence) {
        log.info("replaceText start: $startOffset, end: $endOffset, with '$text'")
        ApplicationManager.getApplication().runWriteAction {
            WriteCommandAction.writeCommandAction(project)
                    .run<Throwable> {
                        document.replaceString(startOffset, endOffset, text)
                        log.info("replaceText")
                    }
        }
    }

    private fun insertText(offset: Int, text: CharSequence) {
        ApplicationManager.getApplication().runWriteAction {
            WriteCommandAction.writeCommandAction(project)
                    .run<Throwable> {
                        document.insertString(offset, text)
                        log.info("insertText")
                    }
        }
    }

    private fun deleteText(start: Int, end: Int) {
        ApplicationManager.getApplication().runWriteAction {
            WriteCommandAction.writeCommandAction(project)
                    .run<Throwable> {
                        document.deleteString(start, end)
                        log.info("deleteText")
                    }
        }
    }

    internal fun onBufferChanged(bufLinesEvent: BufLinesEvent) {
        changedByNvim = true
        try {
            doOnBufferChanged(bufLinesEvent)
        } finally {
            changedByNvim = false
        }
    }

    private fun doOnBufferChanged(bufLinesEvent: BufLinesEvent) {
        log.info("BufferLineEventHandled start changedTick: ${bufLinesEvent.changedTick}")
        if (bufLinesEvent.hasMore) {
            TODO("Handle more")
        }

        val lineData = bufLinesEvent.lineData
        val lastLine = bufLinesEvent.lastLine
        val firstLine = bufLinesEvent.firstLine

        val stringBuilder = StringBuilder()
        lineData.forEachIndexed { index, s ->
            stringBuilder.append(s)
            if (index < lineData.size - 1) {
                stringBuilder.append('\n')
            }
        }
        if (lastLine == -1) {
            setText(stringBuilder)
        }
        else
        {
            val curLineCount = document.lineCount
            // start should include the previous EOL
            when {
                // Deletion
                lineData.isEmpty() -> {
                    val start = when (firstLine) {
                        0 -> 0
                        curLineCount -> document.getLineEndOffset(firstLine - 1)
                        else -> document.getLineStartOffset(firstLine) - 1
                    }
                    val end = when {
                        lastLine > curLineCount -> 0
                        lastLine == curLineCount -> document.getLineEndOffset(lastLine - 1)
                        start == 0 -> document.getLineStartOffset(lastLine)
                        else -> document.getLineStartOffset(lastLine) - 1
                    }
                    deleteText(start, end)
                }
                firstLine == lastLine -> {
                    val start = when (firstLine) {
                        0 -> 0
                        curLineCount -> document.getLineEndOffset(firstLine - 1)
                        else -> document.getLineStartOffset(firstLine) - 1
                    }
                    if (start != 0) {
                        stringBuilder.insert(0, '\n')
                    }
                    else {
                        stringBuilder.append('\n')
                    }
                    // Insertion
                    insertText(start,
                            if (firstLine == curLineCount) stringBuilder
                            else stringBuilder)
                }
                else -> {
                    val start = when (firstLine) {
                        0 -> 0
                        curLineCount -> document.getLineEndOffset(firstLine - 1)
                        else -> document.getLineStartOffset(firstLine) - 1
                    }
                    // Replace the whole end line including EOL
                    val end = when {
                        lastLine > curLineCount -> 0
                        lastLine == curLineCount -> document.getLineEndOffset(lastLine - 1)
                        else -> document.getLineEndOffset(lastLine - 1) + 1
                    }
                    if (firstLine != 0) {
                        stringBuilder.insert(0, '\n')
                    }
                    replaceText(start, end,
                            if (lastLine >= curLineCount) stringBuilder
                            else stringBuilder.append('\n'))
                }
            }
        }
        changedTick = bufLinesEvent.changedTick
        log.info("BufferLineEventHandled changedTick: $changedTick ${document.textLength}")
    }

    override fun close() {
        EditorFactory.getInstance().releaseEditor(editor)
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

