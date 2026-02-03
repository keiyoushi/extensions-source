plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint()
            .editorConfigOverride(mapOf(
                "ktlint_standard_discouraged-comment-location" to "disabled",
                "ktlint_function_signature_body_expression_wrapping" to "default",
                "ktlint_standard_chain-method-continuation" to "disable"
            ))
        trimTrailingWhitespace()
        endWithNewline()
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
