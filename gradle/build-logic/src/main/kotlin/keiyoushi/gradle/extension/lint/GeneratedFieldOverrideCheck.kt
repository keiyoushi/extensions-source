package keiyoushi.gradle.extension.lint

import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.io.Serializable

/**
 * Spotless step that fails the build when a user-authored extension class
 * tries to `override val name|lang|baseUrl|id|versionId` — those properties
 * are owned by the codegen in `kei.plugins.extension`.
 *
 * Wired only from the modern plugin, so legacy extensions are unaffected.
 */
object GeneratedFieldOverrideCheck {
    private val FORBIDDEN = Regex(
        """^\s*override\s+val\s+(name|lang|baseUrl|id|versionId)\b""",
        RegexOption.MULTILINE,
    )

    fun create(): FormatterStep = FormatterStep.create(
        "modern-no-generated-overrides",
        State(),
        State::toFormatter,
    )

    private class State : Serializable {
        fun toFormatter() = FormatterFunc { content ->
            val match = FORBIDDEN.find(content)
            if (match != null) {
                val field = match.groupValues[1]
                throw AssertionError(
                    "'$field' is owned by kei.plugins.extension codegen — remove the override " +
                        "and configure it via the extension { source { … } } DSL instead.",
                )
            }
            content
        }
    }
}
