import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LectorManga.lat"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://lectormangass.com"
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
