import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangahere"
    versionCode = 23
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.mangahere.cc"
        id = 2L
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
