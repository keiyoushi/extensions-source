package eu.kanade.tachiyomi.extension.ja.yanmaga

import app.cash.quickjs.QuickJs

private val INSERT_ADJACENT_HTML_REGEX = Regex(
    """\s*\.\s*insertAdjacentHTML\s*\(\s*['"](beforebegin|afterbegin|beforeend|afterend)['"]\s*,\s*""",
)

/**
 * Get the inserted content from a script containing a bunch of insertAdjacentHTML calls.
 */
internal fun parseInsertAdjacentHtmlScript(script: String, targetName: String = "target"): List<String> =
    QuickJs.create().use { qjs ->
        val cleanedScript = script.split("\n")
            .filterNot {
                it.contains("var $targetName") || it.contains("$targetName.classList")
            }
            .joinToString("\n")
            .replace(INSERT_ADJACENT_HTML_REGEX, ".push(")
        val result = qjs.evaluate(
            """
                const $targetName = [];
                $cleanedScript
                $targetName
            """.trimIndent(),
        )

        (result as Array<*>).map { it as String }
    }
