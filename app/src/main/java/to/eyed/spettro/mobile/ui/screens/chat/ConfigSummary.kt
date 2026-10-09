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
 */
data class ConfigSummary(val text: String, val warning: String? = null) {

    /** Where [warning] sits in [text], or null when nothing warns. */
    val warningRange: IntRange?
        get() {
            val part = warning ?: return null
            val start = text.lastIndexOf(part)
            return if (start < 0) null else start until start + part.length
        }

    companion object {
        /** The option ids the chip names, in the desktop's toolbar order. */
        private val IDS = listOf("mode", "model", "permission")

        /** The permission level that warns: the CLI's "yolo", Don’t ask. */
        private const val UNASKED = "yolo"

        /**
         * The chip for a chat's [options]: the current mode, model and
         * permission level, the level in the desktop's words. Null when the
         * chat advertises none of the three.
         */
        fun of(options: List<AcpConfigOption>): ConfigSummary? {
            var warning: String? = null
            val parts = IDS.mapNotNull { id ->
                val option = options.firstOrNull { it.id == id } ?: return@mapNotNull null
                val value = (option.kind as? AcpConfigOption.Kind.Select)?.current
                if (id == "permission") {
                    // The desktop's one name per permission level — never "YOLO".
                    val name = PermissionCopy.name(value) ?: option.currentLabel.takeIf { it.isNotBlank() }
                    if (value == UNASKED) warning = name
                    name
                } else {
                    option.currentLabel.takeIf { it.isNotBlank() }
                }
            }
            if (parts.isEmpty()) return null
            return ConfigSummary(parts.joinToString(" · "), warning)
        }
    }
}
