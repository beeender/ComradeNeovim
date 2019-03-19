package org.beeender.neovim

class Constants {
    companion object {
        const val FUN_NVIM_BUF_ATTACH = "nvim_buf_attach"
        const val FUN_NVIM_BUF_DETACH = "nvim_buf_detach"
        const val FUN_NVIM_BUF_GET_LINES = "nvim_buf_get_lines"
        const val FUN_NVIM_BUF_GET_NAME = "nvim_buf_get_name"
        const val FUN_NVIM_BUF_SET_LINES = "nvim_buf_set_lines"
        const val FUN_NVIM_CALL_FUNCTION = "nvim_call_function"

        const val MSG_NVIM_BUF_DETACH_EVENT = "nvim_buf_detach_event"
        const val MSG_NVIM_BUF_LINES_EVENT = "nvim_buf_lines_event"
        const val MSG_NVIM_BUF_CHANGEDTICK_EVENT =  "nvim_buf_changedtick_event"
    }
}