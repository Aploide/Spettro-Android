package to.eyed.spettro.mobile.ui.screens.chat

import to.eyed.spettro.mobile.core.acp.AcpConfigOption

/**
 * What the composer's settings chip says: "Coding · GPT-5 · Don’t ask".
 *
 * The desktop splits this over three controls — the tinted mode chip, the
 * model button and the settings chip that names the permission level — and
 * draws the settings chip in the warning ink under Don’t ask
 * (`settings-chip--warn`), so a chat that runs everything unasked never
 * looks like one that asks. A phone's toolbar has room for one chip, so the
 * three share it and only the permission's part carries the warning.
 *
 * @property text the parts, joined by " · ".
 * @property warning the part of [text] to draw in the warning ink, if any.
 * @property tailStart where the permission level's part starts, its " · "
 *   included, or null when the chat reports no level or the level is the
 *   only part. The chip shortens what comes before it and never this: cut
 *   from the end, a narrow chip (a larger font, a long model name) would
 *   drop the permission first, and with it the warning. Without a level
 *   nothing is held back, so a long model name still ends in an ellipsis
 *   rather than pushing the mode out.
 */
data class ConfigSummary(
    val text: String,
    val warning: String? = null,
    val tailStart: Int? = null,
) {

    /** Where [warning] sits in [text], or null when nothing warns. */
    val warningRange: IntRange?
        get() {
            val part = warning ?: return null
            val start = text.lastIndexOf(part)
            return if (start < 0) null else start until start + part.length
        }

    companion object {
        private const val SEPARATOR = " · "

        /** The option ids the chip names, in the desktop's toolbar order. */
        private val IDS = listOf("mode", "model", "permission")

        /** The permission level that warns: the CLI's "yolo", Don’t ask. */
        private const val UNASKED = "yolo"

        /** What the model button says while no model is set, as the desktop's does. */
        const val NO_MODEL = "Choose a model"

        /**
         * The model's name for [value] — its choice's name, else the raw id
         * without its provider prefix — or null when no model is set: a
         * fresh config reports ":" (the CLI joins two empty strings). Port
         * of the desktop's `currentModelName` (ModelMenu.tsx).
         */
        fun modelName(kind: AcpConfigOption.Kind.Select, value: String? = kind.current): String? {
            if (value.isNullOrEmpty()) return null
            val name = kind.groups.asSequence()
                .flatMap { it.options.asSequence() }
                .firstOrNull { it.value == value }
                ?.name
                ?: value.substringAfter(':')
            return name.takeIf { it.isNotBlank() }
        }

        /**
         * The chip for a chat's [options]: the current mode, model and
         * permission level, the level in the desktop's words. Null when the
         * chat advertises none of the three.
         */
        fun of(options: List<AcpConfigOption>): ConfigSummary? {
            var warning: String? = null
            var permission: String? = null
            val parts = IDS.mapNotNull { id ->
                val option = options.firstOrNull { it.id == id } ?: return@mapNotNull null
                val value = (option.kind as? AcpConfigOption.Kind.Select)?.current
                if (id == "permission") {
                    // The desktop's one name per permission level — never "YOLO".
                    val name = PermissionCopy.name(value) ?: option.currentLabel.takeIf { it.isNotBlank() }
                    if (value == UNASKED) warning = name
                    permission = name
                    name
                } else if (id == "model") {
                    (option.kind as? AcpConfigOption.Kind.Select)?.let(::modelName) ?: NO_MODEL
                } else {
                    option.currentLabel.takeIf { it.isNotBlank() }
                }
            }
            if (parts.isEmpty()) return null
            val text = parts.joinToString(SEPARATOR)
            // The level is always the last part (see [IDS]).
            val tailStart = permission
                ?.takeIf { parts.size > 1 }
                ?.let { text.length - it.length - SEPARATOR.length }
            return ConfigSummary(text, warning, tailStart)
        }
    }
}
