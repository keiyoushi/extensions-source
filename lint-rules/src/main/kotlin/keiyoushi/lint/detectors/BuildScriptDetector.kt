package keiyoushi.lint.detectors

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.intellij.psi.PsiElement
import keiyoushi.lint.KEIYOUSHI
import org.jetbrains.uast.UElement

class BuildScriptDetector :
    Detector(),
    GradleScanner {

    private class Block {
        var name: Pair<String, Location>? = null
        var lang: String? = null
        val hosts = mutableListOf<String>()
    }

    private var extensionName: Pair<String, Location>? = null
    private val sources = LinkedHashMap<Any, Block>()
    private val deeplinkHosts = LinkedHashMap<Any, MutableList<Pair<String, Location>>>()

    override fun beforeCheckFile(context: Context) {
        extensionName = null
        sources.clear()
        deeplinkHosts.clear()
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        val text = value.unquote()
        when {
            parent == "keiyoushi" && property == "name" && text != null -> {
                extensionName = text to context.getLocation(valueCookie)
            }

            parent == "source" && parentParent == "keiyoushi" -> {
                val block = sources.getOrPut(statementCookie.blockKey()) { Block() }
                when (property) {
                    "name" -> if (text != null) block.name = text to context.getLocation(valueCookie)
                    "lang" -> block.lang = text
                    "baseUrl" -> text?.let(::host)?.let(block.hosts::add)
                    "id" -> context.report(
                        EXPLICIT_ID,
                        propertyCookie,
                        context.getLocation(statementCookie),
                        "`id` only belongs on a source that was renamed or changed language, set to its old id; " +
                            "drop it on a new source",
                    )
                }
            }
        }
    }

    override fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        parentParent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any,
    ) {
        when {
            statement == "host" && parent == "deeplink" -> {
                val host = unnamedArguments.firstOrNull()?.unquote() ?: return
                deeplinkHosts.getOrPut(cookie.blockKey()) { mutableListOf() } += host to context.getLocation(cookie)
            }

            // baseUrl { mirrors("https://a", "https://b") } / custom("https://a")
            (statement == "mirrors" || statement == "custom") && parent == "baseUrl" -> {
                val block = sources.getOrPut(cookie.blockKey(levels = 2)) { Block() }
                unnamedArguments.mapNotNull { it.substringAfterLast(" to ").unquote()?.let(::host) }.forEach(block.hosts::add)
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val gradle = context as? GradleContext ?: return
        val langs = sources.values.mapNotNull { it.lang }

        extensionName?.let { (name, location) -> checkLanguageSuffix(gradle, name, location, langs) }
        sources.values.forEach { block ->
            val (name, location) = block.name ?: return@forEach
            checkLanguageSuffix(gradle, name, location, listOfNotNull(block.lang))
            if (sources.size == 1 && name == extensionName?.first) {
                gradle.report(
                    REDUNDANT_SOURCE_NAME,
                    location,
                    "`name` defaults to the extension name `\"$name\"`; remove it",
                )
            }
        }

        // A deeplink block without host() gets every source's baseUrl host, mirrors included; any host() replaces them
        val sourceHosts = sources.values.flatMap { it.hosts }.toSet()
        deeplinkHosts.values.forEach { hosts ->
            if (sourceHosts.isEmpty() || hosts.map { it.first }.toSet() != sourceHosts) return@forEach
            gradle.report(
                REDUNDANT_DEEPLINK_HOST,
                hosts.first().second,
                "These `host(...)` calls list exactly the `baseUrl` hosts, which a `deeplink {}` without `host()` uses; remove them",
            )
        }
    }

    private fun checkLanguageSuffix(context: GradleContext, name: String, location: Location, langs: List<String>) {
        val suffix = SUFFIX.find(name)?.groupValues?.get(1) ?: return
        val matches = langs.any { lang -> lang.equals(suffix, ignoreCase = true) || lang.split('-').any { it.equals(suffix, ignoreCase = true) } } ||
            suffix.lowercase() in LANGUAGE_WORDS
        if (!matches) return
        context.report(
            LANGUAGE_SUFFIX,
            location,
            "Remove the language suffix from `\"$name\"`; the app already groups sources by language",
        )
    }

    // The cookie is the assignment/call; its parents up to the `source { }` lambda identify the block
    private fun Any.blockKey(levels: Int = 1): Any {
        var element = (this as? UElement)?.sourcePsi ?: this as? PsiElement ?: return this
        repeat(levels) {
            var parent: PsiElement? = element.parent
            while (parent != null && parent.javaClass.simpleName !in BLOCK_TYPES) parent = parent.parent
            element = parent ?: return element
        }
        return element
    }

    companion object {
        private val BLOCK_TYPES = setOf("KtBlockExpression", "GrClosableBlock")
        private val SUFFIX = Regex("""\s*[-(\[]?\s*\b([A-Za-z]{2}(?:-[A-Za-z]{2})?|English|French|Spanish|Portuguese|Indonesian|Turkish|Arabic|Russian|Vietnamese|Thai|Chinese|Japanese|Korean)\b\s*[)\]]?\s*$""")
        private val LANGUAGE_WORDS = setOf(
            "english", "french", "spanish", "portuguese", "indonesian", "turkish", "arabic", "russian",
            "vietnamese", "thai", "chinese", "japanese", "korean",
        )

        private fun String.unquote(): String? = trim().takeIf { it.length >= 2 && it.startsWith('"') && it.endsWith('"') }?.drop(1)?.dropLast(1)

        private fun host(url: String): String? = Regex("""^https?://([^/:]+)""").find(url)?.groupValues?.get(1)

        private val IMPLEMENTATION = Implementation(BuildScriptDetector::class.java, Scope.GRADLE_SCOPE)

        private fun gradleIssue(id: String, brief: String, explanation: String, enabledByDefault: Boolean = true) = Issue.create(
            id = id,
            briefDescription = brief,
            explanation = explanation,
            category = KEIYOUSHI,
            priority = 5,
            severity = com.android.tools.lint.detector.api.Severity.WARNING,
            implementation = IMPLEMENTATION,
            enabledByDefault = enabledByDefault,
        )

        val LANGUAGE_SUFFIX = gradleIssue(
            "KeiNameLanguageSuffix",
            "Language suffix in a source name",
            "Don't add a language suffix (`\"MySite EN\"`) to `name`; the app groups sources by language.",
        )

        val REDUNDANT_SOURCE_NAME = gradleIssue(
            "KeiRedundantSourceName",
            "`source { name }` equal to the extension name",
            "A `source {}` block's `name` defaults to the top-level `name`.",
        )

        val REDUNDANT_DEEPLINK_HOST = gradleIssue(
            "KeiRedundantDeeplinkHost",
            "Deeplink host equal to the `baseUrl` host",
            "A `deeplink {}` without `host()` uses the hosts of every source's `baseUrl`, mirrors included. Any `host()` replaces that list.",
        )

        val EXPLICIT_ID = gradleIssue(
            "KeiExplicitSourceId",
            "Explicit source `id`",
            """
            Set `id` only to keep the old id of a source that was renamed or changed language. A new source, or \
            one whose URLs changed incompatibly (bump `versionId` instead), must not set it.

            Off by default: lint can't tell a new source from a renamed one. Enable it when reviewing a new source.
            """.trimIndent(),
            enabledByDefault = false,
        )

        val ISSUES = listOf(LANGUAGE_SUFFIX, REDUNDANT_SOURCE_NAME, REDUNDANT_DEEPLINK_HOST, EXPLICIT_ID)
    }
}
