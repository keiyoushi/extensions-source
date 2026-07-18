import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Buon Dua"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://buondua.com"
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
