import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaIsekaiThai"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "th"
        baseUrl = "https://www.mangaisekaithai.net"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
