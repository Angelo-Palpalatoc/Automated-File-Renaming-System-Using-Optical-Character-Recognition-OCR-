package com.local.secrename.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputRequestTest {
    @Test fun rememberedAccessibleFolderContinuesWithoutPromptForEitherAction() {
        InputAction.entries.forEach { action ->
            val ready = InputRequest(1, action).folderChecked(true)
            assertEquals(InputStep.READY, ready.step)
            assertEquals(action, ready.action)
        }
    }

    @Test fun missingOrInaccessibleFolderRequiresUserChoice() {
        val prompt = InputRequest(2, InputAction.FILE).folderChecked(false)
        assertEquals(InputStep.ASK_FOLDER, prompt.step)
        assertEquals(InputStep.ASK_FOLDER, prompt.folderAccepted().step)
    }

    @Test fun folderChoicePreservesTheOriginalActionUntilValidated() {
        InputAction.entries.forEach { action ->
            val waiting = InputRequest(3, action).folderChecked(false).chooseFolder().pickerOpened()
            assertEquals(InputStep.WAIT_FOR_FOLDER, waiting.step)
            assertEquals(InputStep.READY, waiting.folderAccepted().step)
            assertEquals(action, waiting.folderAccepted().action)
            assertEquals(3L, waiting.folderAccepted().id)
        }
    }

    @Test fun pickerLaunchIsNoLongerPendingOnceOpened() {
        val waiting = InputRequest(4, InputAction.FILE).folderChecked(false).chooseFolder().pickerOpened()
        assertEquals(InputStep.WAIT_FOR_FOLDER, waiting.pickerOpened().step)
        assertEquals(InputStep.WAIT_FOR_FOLDER, waiting.chooseFolder().step)
    }

    @Test fun onlyThePromptCanRequestAFolderPicker() {
        assertEquals(InputStep.CHECK_FOLDER, InputRequest(5, InputAction.SCAN).chooseFolder().step)
        val ready = InputRequest(5, InputAction.SCAN).folderChecked(true)
        assertEquals(InputStep.READY, ready.chooseFolder().step)
        assertEquals(InputStep.READY, ready.folderChecked(false).step)
    }

    @Test fun backgroundingClearsPendingLaunchButKeepsDestination() {
        val state = DemoState(outputReady = true, outputId = "remembered", inputRequest = InputRequest(6, InputAction.SCAN).folderChecked(true))
        val cleared = state.clearDocument()
        assertNull(cleared.inputRequest)
        assertEquals(state.outputId, cleared.outputId)
    }
}
