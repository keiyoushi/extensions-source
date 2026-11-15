import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

// Shared code other extensions call through lib:runtime. Its one source stays out of Browse and
// only holds settings: the modules and the licenses in assets/licenses.
keiyoushi {
    name = "Keiyoushi Runtime"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    runtime {
        provides("webviewpatch", api = 1)
    }
    source {
        lang = "all"
        baseUrl = "https://github.com/keiyoushi/extensions-source"
    }
}

android {
    testOptions {
        // Forward `-Dxrw.corpus=<dir>` from the Gradle command line into the test JVM, as the corpus-gated
        // locator tests' assumeTrue instructs. Unset, they get "" and skip; the corpus (real WebView builds,
        // ~150 MB each) is never committed.
        unitTests.all { it.systemProperty("xrw.corpus", System.getProperty("xrw.corpus") ?: "") }
    }
}

dependencies {
    implementation(project(":lib:runtime"))
    implementation(project(":lib:webviewpatch"))
}
