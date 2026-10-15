package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A half-written prompt outlives the chat screen it was typed in. */
class DraftsTest {
    @Test
    fun eachChatKeepsItsOwnDraft() {
        val drafts = Drafts()
        drafts.save("a", "fix the slider")
        drafts.save("b", "explain the store")
        assertEquals("fix the slider", drafts.load("a"))
        assertEquals("explain the store", drafts.load("b"))
        assertEquals("", drafts.load("c"))
    }

    @Test
    fun anEmptiedFieldKeepsNothing() {
        val storage = Drafts.Storage.InMemory()
        val drafts = Drafts(storage)
        drafts.save("a", "draft")
        drafts.save("a", "")
        assertNull(storage.get("draft.a"))
        assertEquals("", drafts.load("a"))
    }

    @Test
    fun theNewSessionViewHasADraftOfItsOwn() {
        val drafts = Drafts()
        drafts.save(null, "start here")
        assertEquals("start here", drafts.load(null))
        assertEquals("start here", drafts.load(""))
        assertEquals("", drafts.load("a"))
    }

    @Test
    fun draftsSurviveANewStoreOnTheSameStorage() {
        val storage = Drafts.Storage.InMemory()
        Drafts(storage).save("a", "kept across launches")
        assertEquals("kept across launches", Drafts(storage).load("a"))
    }
}
