import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import io.github.keiyoushi.gradle.internal.extensions.alias
import io.github.keiyoushi.gradle.internal.extensions.libs
import io.github.keiyoushi.gradle.internal.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.Serializable

@Suppress("UNUSED")
class SpotlessPlugin : Plugin<Project> {
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
                addStep(RandomUAChecker.toFormatterStep())
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

private fun Project.spotless(block: SpotlessExtension.() -> Unit) {
    extensions.configure(block)
}

private class RandomUAChecker : Serializable {

    fun getFormatter() = FormatterFunc { content ->
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

    companion object {
        fun toFormatterStep(): FormatterStep = FormatterStep.create(
            "randomua-requires-getMangaUrl",
            RandomUAChecker(),
            RandomUAChecker::getFormatter,
        )
    }
}
