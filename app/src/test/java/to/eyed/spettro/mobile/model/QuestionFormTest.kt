package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.model.QuestionForm.Draft

/** The footer rules of the desktop's QuestionCard.tsx. */
class QuestionFormTest {

    @Test
    fun anUntouchedFormIsLeftWithoutAsking() {
        assertFalse(QuestionForm.dirty(listOf(Draft(), Draft(custom = "   "))))
    }

    @Test
    fun aPickTypedWordsOrANoteMakeItDirty() {
        assertTrue(QuestionForm.dirty(listOf(Draft(), Draft(selected = listOf("a")))))
        assertTrue(QuestionForm.dirty(listOf(Draft(custom = "my words"))))
        assertTrue(QuestionForm.dirty(listOf(Draft(note = "careful"))))
    }

    @Test
    fun theSkippedHintCountsUnansweredQuestionsOnTheLastStep() {
        val drafts = listOf(Draft(selected = listOf("a")), Draft(), Draft(note = "only a note"))
        assertEquals("2 questions will be sent as skipped", QuestionForm.skippedHint(true, drafts))
        assertEquals(
            "1 question will be sent as skipped",
            QuestionForm.skippedHint(true, listOf(Draft(custom = "x"), Draft())),
        )
    }

    @Test
    fun noHintBeforeTheLastStepForOneQuestionOrWithNothingOrEverythingAnswered() {
        assertNull(QuestionForm.skippedHint(false, listOf(Draft(custom = "x"), Draft())))
        assertNull(QuestionForm.skippedHint(true, listOf(Draft())))
        assertNull(QuestionForm.skippedHint(true, listOf(Draft(), Draft())))
        assertNull(QuestionForm.skippedHint(true, listOf(Draft(custom = "x"), Draft(selected = listOf("b")))))
    }
}
