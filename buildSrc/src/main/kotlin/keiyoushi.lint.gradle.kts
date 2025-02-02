plugins {
    id("com.diffplug.spotless")
}

spotless {
    ratchetFrom = "105f615b339e681a630f21dc0d363b8ca1cb17d5"

    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint()
            .editorConfigOverride(mapOf(
                "ktlint_standard_discouraged-comment-location" to "disabled",
                "ktlint_function_signature_body_expression_wrapping" to "default",
                "ktlint_standard_no-empty-first-line-in-class-body" to "disable",
                "ktlint_standard_chain-method-continuation" to "disable"
            ))
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
    named("preBuild") {
        dependsOn(
            tasks.getByName("spotlessCheck")
        )
    }

    if (System.getenv("CI") != "true") {
        named("spotlessCheck") {
            dependsOn(
                tasks.getByName("spotlessApply")
            )
        }
    }
}
