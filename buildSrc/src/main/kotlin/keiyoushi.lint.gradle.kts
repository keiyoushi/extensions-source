import java.io.File

plugins {
    id("com.diffplug.spotless")
}

spotless {
    val isCi = System.getenv("CI") == "true"

    val isSparseCheckout = if (isCi) {
        false // irrelevant in CI; we always ratchet
    } else {
        val sparseCheckoutFile = File(project.rootDir, ".git/info/sparse-checkout")
        if (!sparseCheckoutFile.exists() || sparseCheckoutFile.readText().isBlank()) {
            false
        } else { // sparse-checkout file sometimes exists even when disabled
            val enabledRegex = Regex("(?m)^\\s*sparseCheckout\\s*=\\s*true")
            val gitConfig = File(project.rootDir, ".git/config")
            val gitWorktreeConfig = File(project.rootDir, ".git/config.worktree")

            (gitConfig.exists() && enabledRegex.containsMatchIn(gitConfig.readText())) ||
                    (gitWorktreeConfig.exists() && enabledRegex.containsMatchIn(gitWorktreeConfig.readText()))
        }
    }

    // In CI we always ratchet; locally we ratchet when NOT in a sparse checkout.
    // Ratcheting breaks in sparse checkouts with "Invalid path" and "zero length name" errors
    if (isCi || !isSparseCheckout) {
        ratchetFrom = "68ed874a42038f4efd001c555cba9bf7de8474d7"
    }

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
