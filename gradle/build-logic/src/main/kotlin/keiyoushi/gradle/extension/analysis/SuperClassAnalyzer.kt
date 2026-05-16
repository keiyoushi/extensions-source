package keiyoushi.gradle.extension.analysis

import java.io.File

data class SuperClassAnalysis(
    val declaresConfigurable: Boolean,
    val overridesSetupPrefs: Boolean,
)

// Regex scan — does not detect ConfigurableSource inherited transitively through
// another base class. Treated as non-configurable in that case.
fun analyzeSuperClass(srcDir: File, extensionClass: String): SuperClassAnalysis {
    if (!srcDir.exists()) return SuperClassAnalysis(false, false)
    val files = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    val classHeader = Regex("""\b(?:abstract\s+|open\s+|sealed\s+)*class\s+$extensionClass\b[^{]*\{""")
    val setupOverride = Regex("""\boverride\s+fun\s+setupPreferenceScreen\s*\(""")
    var declares = false
    var overrides = false
    for (f in files) {
        val text = f.readText()
        classHeader.find(text)?.let { m ->
            if (m.value.contains("ConfigurableSource")) declares = true
        }
        if (setupOverride.containsMatchIn(text)) overrides = true
    }
    return SuperClassAnalysis(declares, overrides)
}
