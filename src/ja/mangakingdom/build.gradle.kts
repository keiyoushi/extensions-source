import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Kingdom"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://comic.k-manga.jp"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
