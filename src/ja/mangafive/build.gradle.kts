import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-5"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://manga-5.com"
    }
}

dependencies {
    implementation(project(":lib:publus"))
}
