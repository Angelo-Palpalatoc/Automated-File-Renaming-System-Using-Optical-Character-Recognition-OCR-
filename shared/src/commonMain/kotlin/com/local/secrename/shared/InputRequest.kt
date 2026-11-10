package com.local.secrename.shared

enum class InputAction { SCAN, FILE }
enum class InputStep { CHECK_FOLDER, ASK_FOLDER, OPEN_FOLDER_PICKER, WAIT_FOR_FOLDER, READY }

/** A foreground-only request. Each picker launch must be consumed before opening Android UI. */
data class InputRequest(val id: Long, val action: InputAction, val step: InputStep = InputStep.CHECK_FOLDER) {
    fun folderChecked(available: Boolean): InputRequest =
        if (step == InputStep.CHECK_FOLDER) copy(step = if (available) InputStep.READY else InputStep.ASK_FOLDER) else this

    fun chooseFolder(): InputRequest =
        if (step == InputStep.ASK_FOLDER) copy(step = InputStep.OPEN_FOLDER_PICKER) else this

    fun pickerOpened(): InputRequest =
        if (step == InputStep.OPEN_FOLDER_PICKER) copy(step = InputStep.WAIT_FOR_FOLDER) else this

    fun folderAccepted(): InputRequest =
        if (step == InputStep.WAIT_FOR_FOLDER) copy(step = InputStep.READY) else this
}
