package keiyoushi.lint.detectors

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.BinaryResourceScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import keiyoushi.lint.KEIYOUSHI
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

class IconDetector :
    Detector(),
    BinaryResourceScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.MIPMAP

    override fun checkBinaryResource(context: ResourceContext) {
        val file = context.file
        if (file.name != ICON) return
        val density = file.parentFile.name.removePrefix("mipmap-")
        val expected = SIZES[density] ?: return
        val image = runCatching { ImageIO.read(file) }.getOrNull() ?: return
        val location = Location.create(file)

        if (image.width != expected || image.height != expected) {
            context.report(
                ICON_SIZE,
                location,
                "`mipmap-$density/$ICON` is ${image.width}×${image.height}, expected $expected×$expected",
            )
            return
        }

        // The shape only needs reporting once, on the largest density
        if (density == "xxxhdpi") checkShape(context, image, location)
    }

    /**
     * Matches the "Square" shape of the Icon Generator linked in CONTRIBUTING.md: on a 48dp canvas, a 38×38dp
     * rounded square (corner radius 3dp) at (5dp, 5dp), so a transparent 5dp margin that only the shadow reaches.
     */
    private fun checkShape(context: Context, image: BufferedImage, location: Location) {
        val dp = image.width / 48.0
        val size = image.width
        val mid = size / 2
        val start = (5 * dp).toInt()
        val end = (43 * dp).toInt() - 1

        val margin = listOf(2 to mid, size - 3 to mid, mid to 2, mid to size - 3)
        val insideEdges = listOf(start + 2 to mid, end - 2 to mid, mid to start + 2, mid to end - 2)
        val cornerTips = listOf(start + 1 to start + 1, end - 1 to start + 1, start + 1 to end - 1, end - 1 to end - 1)
        val inset = (2 * dp).toInt()
        val nearCorners = listOf(start + inset to start + inset, end - inset to start + inset, start + inset to end - inset, end - inset to end - inset)

        val problem = when {
            margin.any { (x, y) -> image.alpha(x, y) > MAX_SHADOW_ALPHA } -> "fills the whole canvas instead of leaving the 5dp margin"
            insideEdges.any { (x, y) -> image.alpha(x, y) < 250 } -> "isn't a filled square (no solid background, or another shape)"
            nearCorners.any { (x, y) -> image.alpha(x, y) < 250 } -> "has corners rounded too much (circle or squircle)"
            cornerTips.any { (x, y) -> image.alpha(x, y) > 200 } -> "has square corners"
            hasBorder(image, start, end, dp) -> "has a border, or a foreground whose background doesn't match the square's"
            else -> return
        }
        context.report(
            ICON_SHAPE,
            location,
            "The icon $problem; generate it with the Icon Generator linked in CONTRIBUTING.md (Square shape)",
        )
    }

    /** A uniform ring just inside the square's edge that differs from a uniform ring further in. */
    private fun hasBorder(image: BufferedImage, start: Int, end: Int, dp: Double): Boolean {
        val outer = ring(image, start, end, (start + 1.5 * dp).toInt())
        val inner = ring(image, start, end, (start + 4 * dp).toInt())
        if (!outer.isUniform() || !inner.isUniform()) return false
        return colorDistance(outer.first(), inner.first()) > 60
    }

    private fun ring(image: BufferedImage, start: Int, end: Int, offset: Int): List<Int> {
        val opposite = start + end - offset
        val from = start + (end - start) / 5
        val to = end - (end - start) / 5
        return (from until to step 4).flatMap { i ->
            listOf(image.getRGB(i, offset), image.getRGB(i, opposite), image.getRGB(offset, i), image.getRGB(opposite, i))
        }
    }

    private fun List<Int>.isUniform(): Boolean = all { colorDistance(it, first()) < 24 }

    private fun colorDistance(a: Int, b: Int): Int = abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))

    private fun BufferedImage.alpha(x: Int, y: Int): Int = getRGB(x, y) ushr 24

    override fun afterCheckEachProject(context: Context) {
        val res = File(context.project.dir, "res")
        val buildFile = File(context.project.dir, "build.gradle.kts")
        if (!buildFile.isFile || "kei.plugins.extension" !in buildFile.readText()) return

        res.walkTopDown().filter { it.name == HI_RES }.forEach {
            context.report(LEFTOVER_HI_RES, Location.create(it), "Remove `$HI_RES`; only the mipmap icons are used")
        }

        // A themed extension without icons uses its theme's, which the theme library merges in
        val theme = THEME.find(buildFile.readText())?.groupValues?.get(1)
        val root = generateSequence(context.project.dir) { it.parentFile }.firstOrNull { File(it, "lib-multisrc").isDirectory }
        val themeRes = if (theme != null && root != null) File(root, "lib-multisrc/$theme/res") else null
        val hasOwnIcons = SIZES.keys.any { File(res, "mipmap-$it/$ICON").isFile }
        val missing = SIZES.keys.filter { !File(res, "mipmap-$it/$ICON").isFile }
        val inherited = !hasOwnIcons && themeRes != null && SIZES.keys.all { File(themeRes, "mipmap-$it/$ICON").isFile }
        if (missing.isNotEmpty() && !inherited) {
            context.report(
                ICON_SIZE,
                Location.create(buildFile),
                "Missing `$ICON` for ${missing.joinToString { "`mipmap-$it`" }}",
            )
        }
    }

    companion object {
        private const val ICON = "ic_launcher.png"
        private const val HI_RES = "web_hi_res_512.png"

        // The generator's outer shadow reaches about alpha 45 below the square
        private const val MAX_SHADOW_ALPHA = 100
        private val THEME = Regex("""\btheme\s*=\s*"(\w+)"""")
        private val SIZES = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)

        private val IMPLEMENTATION = Implementation(IconDetector::class.java, Scope.BINARY_RESOURCE_FILE_SCOPE)

        private fun iconIssue(id: String, brief: String, explanation: String) = Issue.create(
            id = id,
            briefDescription = brief,
            explanation = explanation,
            category = KEIYOUSHI,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        val ICON_SIZE = iconIssue(
            "KeiIconSize",
            "Extension icon missing or wrongly sized",
            "Every extension ships `ic_launcher.png` in mdpi (48), hdpi (72), xhdpi (96), xxhdpi (144) and xxxhdpi (192).",
        )

        val ICON_SHAPE = iconIssue(
            "KeiIconShape",
            "Extension icon doesn't follow the convention",
            "Extension icons come from the Icon Generator linked in CONTRIBUTING.md with the Square shape: a solid " +
                "38dp rounded square (3dp corners) centred on the 48dp canvas, with a transparent 5dp margin and no border.",
        )

        val LEFTOVER_HI_RES = iconIssue(
            "KeiLeftoverHiResIcon",
            "`web_hi_res_512.png` left in",
            "The Icon Generator also produces `web_hi_res_512.png`, which extensions don't use; remove it.",
        )

        val ISSUES = listOf(ICON_SIZE, ICON_SHAPE, LEFTOVER_HI_RES)
    }
}
