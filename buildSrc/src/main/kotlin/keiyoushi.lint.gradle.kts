plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint()
            .editorConfigOverride(mapOf(
                "max_line_length" to 2147483647,
            ))
        trimTrailingWhitespace()
        endWithNewline()

        custom("randomua-requires-getMangaUrl-override") { fileContent ->
            if ("keiyoushi.lib.randomua" in fileContent &&
                "override fun getMangaUrl(" !in fileContent
            ) {
                throw AssertionError(
                    "usage of :lib:randomua requires override of getMangaUrl()",
                )
            }
            fileContent
        }
    }

    java {
        target("**/*.java")
        targetExclude("**/build/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("gradle") {
        target("**/*.gradle")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks {
    val spotlessTask = if (System.getenv("CI") != "true") "spotlessApply" else "spotlessCheck"
    named("preBuild") {
        dependsOn(tasks.getByName(spotlessTask))
    }
}
