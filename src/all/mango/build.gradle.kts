import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mango"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl {
            custom("http://127.0.0.1:9000")
        }
    }
}

dependencies {
    implementation("info.debatty:java-string-similarity:2.0.0")
}
