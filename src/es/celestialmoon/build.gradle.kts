import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Celestial Moon"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://celestialmoonscan.es"
        // ZeistManga -> MangaThemesia
        versionId = 2
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
