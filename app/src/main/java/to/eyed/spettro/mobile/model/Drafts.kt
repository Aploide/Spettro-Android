package to.eyed.spettro.mobile.model

/**
 * What was typed but not sent, per chat — the desktop's state/drafts.ts.
 * Kept out of the composer's own state, which goes with the chat screen:
 * going back to the list, opening another chat, or the app being closed
 * used to throw the half-written prompt away. Text only, as on the desktop:
 * images are too big to keep and cheap to pick again.
 */
class Drafts(private val storage: Storage = Storage.InMemory()) {
    /** Where drafts live; the app keeps them in SharedPreferences. */
    interface Storage {
        fun get(key: String): String?

        /** A null [value] removes [key]. */
        fun put(key: String, value: String?)

        class InMemory : Storage {
            private val map = HashMap<String, String>()
            override fun get(key: String): String? = map[key]
            override fun put(key: String, value: String?) {
                if (value == null) map.remove(key) else map[key] = value
            }
        }
    }

    /** [chatId]'s draft; null or empty is the new-session view's. */
    fun load(chatId: String?): String = storage.get(key(chatId)).orEmpty()

    /** Keeps [text] for [chatId]; an empty field keeps nothing. */
    fun save(chatId: String?, text: String) {
        val key = key(chatId)
        val value = text.ifEmpty { null }
        if (storage.get(key) != value) storage.put(key, value)
    }

    private fun key(chatId: String?) = PREFIX + (chatId?.takeIf { it.isNotEmpty() } ?: NEW_SESSION)

    companion object {
        /** The new-session view's field (the desktop's NEW_SESSION_DRAFT). */
        const val NEW_SESSION = "new"
        private const val PREFIX = "draft."
    }
}
