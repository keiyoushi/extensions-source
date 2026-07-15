import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Emperor Scan"
    versionCode = 12
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://imperiomanhua.com"
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
