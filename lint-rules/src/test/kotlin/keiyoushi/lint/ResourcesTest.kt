package keiyoushi.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.image
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kts
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Issue
import keiyoushi.lint.detectors.BuildScriptDetector
import keiyoushi.lint.detectors.IconDetector
import org.junit.Test

class ResourcesTest {

    private companion object {
        const val BLUE = 0xFF336699.toInt()
    }

    private fun gradle(issue: Issue, script: String) = lint()
        .files(kts(script).indented())
        .issues(issue)
        .allowMissingSdk()
        .run()

    @Test
    fun languageSuffix() {
        gradle(
            BuildScriptDetector.LANGUAGE_SUFFIX,
            """
            keiyoushi {
                name = "Example"
                source {
                    name = "Example EN"
                    lang = "en"
                    baseUrl = "https://example.com"
                }
                source {
                    name = "Example (pt-BR)"
                    lang = "pt-BR"
                    baseUrl = "https://example.com.br"
                }
                source {
                    name = "Toon GO"
                    lang = "es"
                    baseUrl = "https://example.es"
                }
                source {
                    name = "Sushiscan.fr"
                    lang = "fr"
                    baseUrl = "https://example.fr"
                }
            }
            """,
        ).expectWarningCount(2)
    }

    @Test
    fun redundantSourceName() {
        gradle(
            BuildScriptDetector.REDUNDANT_SOURCE_NAME,
            """
            keiyoushi {
                name = "Example"
                source {
                    name = "Example"
                    lang = "en"
                    baseUrl = "https://example.com"
                }
            }
            """,
        ).expectWarningCount(1)
    }

    @Test
    fun redundantDeeplinkHost() {
        gradle(
            BuildScriptDetector.REDUNDANT_DEEPLINK_HOST,
            """
            keiyoushi {
                name = "Example"
                source {
                    lang = "en"
                    baseUrl = "https://example.com"
                }
                deeplink {
                    host("example.com")
                    path("/manga/..*")
                }
                deeplink {
                    host("example.com")
                    host("www.example.com")
                    path("/manga/..*")
                }
            }
            """,
        ).expectWarningCount(1)
    }

    @Test
    fun explicitId() {
        gradle(
            BuildScriptDetector.EXPLICIT_ID,
            """
            keiyoushi {
                name = "Example"
                source {
                    lang = "en"
                    baseUrl = "https://example.com"
                    id = 1234567890123456789L
                }
            }
            """,
        ).expectWarningCount(1)
    }

    // region Icons

    private val buildFile = kts("plugins { alias(kei.plugins.extension) }")

    private fun icons(xxxhdpi: TestFile, hdpiSize: Int = 72): Array<TestFile> = arrayOf(
        buildFile,
        square("mdpi", 48),
        square("hdpi", hdpiSize),
        square("xhdpi", 96),
        square("xxhdpi", 144),
        xxxhdpi,
    )

    /** The Icon Generator's Square shape: a 38dp square at 5dp on a 48dp canvas, corners cut by [notch] px. */
    private fun square(density: String, size: Int, color: Int = BLUE, notch: Int = size / 64 + 1): TestFile.ImageTestFile {
        val dp = size / 48
        val start = 5 * dp
        val side = 38 * dp
        // fill(x, y, width, height, color)
        return image("res/mipmap-$density/ic_launcher.png", size, size)
            .fill(0x00000000)
            .fill(start + notch, start, side - 2 * notch, side, color)
            .fill(start, start + notch, side, side - 2 * notch, color)
    }

    // Extensions keep `res/` at the module root, like lint's non-Gradle test projects
    private fun icon(issue: Issue, vararg files: TestFile) = lint().files(*files).issues(issue).allowMissingSdk().useTestProjectImplementation(true).run()

    @Test
    fun goodIcons() {
        icon(IconDetector.ICON_SHAPE, *icons(square("xxxhdpi", 192))).expectClean()
        icon(IconDetector.ICON_SIZE, *icons(square("xxxhdpi", 192))).expectClean()
    }

    @Test
    fun themedExtensionInheritsIcons() {
        val themed = arrayOf(
            kts("src/xx/themed/build.gradle.kts", "plugins { alias(kei.plugins.extension) }\nkeiyoushi { theme = \"madara\" }"),
            image("lib-multisrc/madara/res/mipmap-mdpi/ic_launcher.png", 48, 48).fill(BLUE),
            image("lib-multisrc/madara/res/mipmap-hdpi/ic_launcher.png", 72, 72).fill(BLUE),
            image("lib-multisrc/madara/res/mipmap-xhdpi/ic_launcher.png", 96, 96).fill(BLUE),
            image("lib-multisrc/madara/res/mipmap-xxhdpi/ic_launcher.png", 144, 144).fill(BLUE),
            image("lib-multisrc/madara/res/mipmap-xxxhdpi/ic_launcher.png", 192, 192).fill(BLUE),
        )
        icon(IconDetector.ICON_SIZE, *themed).expectClean()
        icon(IconDetector.ICON_SIZE, buildFile).expectWarningCount(1)
    }

    @Test
    fun leftoverHiResIsAnError() {
        val hiRes = image("res/web_hi_res_512.png", 512, 512).fill(BLUE)
        icon(IconDetector.LEFTOVER_HI_RES, *icons(square("xxxhdpi", 192)), hiRes).expectErrorCount(1)
    }

    @Test
    fun wrongSize() {
        icon(IconDetector.ICON_SIZE, *icons(square("xxxhdpi", 192), hdpiSize = 64)).expectWarningCount(1)
    }

    @Test
    fun fullBleed() {
        val icon = image("res/mipmap-xxxhdpi/ic_launcher.png", 192, 192).fill(BLUE)
        icon(IconDetector.ICON_SHAPE, *icons(icon)).expectWarningCount(1)
    }

    @Test
    fun circle() {
        icon(IconDetector.ICON_SHAPE, *icons(square("xxxhdpi", 192, notch = 40))).expectWarningCount(1)
    }

    @Test
    fun border() {
        val icon = square("xxxhdpi", 192, color = 0xFF000000.toInt())
            .fill(28, 28, 136, 136, 0xFFFFFFFF.toInt())
        icon(IconDetector.ICON_SHAPE, *icons(icon)).expectWarningCount(1)
    }

    // endregion
}
