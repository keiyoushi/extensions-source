plugins {
    id("com.diffplug.spotless")
}

spotless {
    ratchetFrom = "9aae35fe3b7614cd08c927db66d65d94ec69a5d3"

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
//    val spotlessTask = if (System.getenv("CI") != "true") "spotlessApply" else "spotlessCheck"
//    named("preBuild") {
//        dependsOn(tasks.getByName(spotlessTask))
//    }
}
