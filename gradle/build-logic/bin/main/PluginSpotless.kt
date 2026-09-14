import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import keiyoushi.gradle.extensions.alias
import keiyoushi.gradle.extensions.libs
import keiyoushi.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.invoke
import java.io.Serializable

@Suppress("UNUSED")
class PluginSpotless : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.spotless)
        }

        // Configuration should be synced with [/gradle/build-logic/build.gradle.kts]
        val ktlintVersion = libs.ktlint.bom.get().version
        spotless {
            kotlin {
                target("src/**/*.kt", "*.kts")
                ktlint(ktlintVersion)
                    .editorConfigOverride(
                        mapOf(
                            "max_line_length" to 2147483647,
                        ),
                    )
                trimTrailingWhitespace()
                endWithNewline()
                addStep(RandomUACheck.create())
            }

            java {
                target("src/**/*.java")
                googleJavaFormat()
                removeUnusedImports()
                trimTrailingWhitespace()
                endWithNewline()
            }

            format("gradle") {
                target("*.gradle")
                trimTrailingWhitespace()
                endWithNewline()
            }

            format("xml") {
                target("src/**/*.xml")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }
}

private object RandomUACheck {
    fun create(): FormatterStep = FormatterStep.create(
        "randomua-requires-getMangaUrl",
        State(),
        State::toFormatter,
    )

    private class State : Serializable {
        fun toFormatter() = FormatterFunc { content ->
            if ("package keiyoushi.lib.randomua" !in content &&
                "keiyoushi.lib.randomua" in content &&
                "override fun getMangaUrl(" !in content
            ) {
                throw AssertionError(
                    "usage of :lib:randomua requires override of getMangaUrl()",
                )
            }
            content
        }
    }
}

private fun Project.spotless(block: SpotlessExtension.() -> Unit) {
    extensions.configure(block)
}
