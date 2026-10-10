import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kumanga"
    versionCode = 16
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://www.kumanga.com"
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}
