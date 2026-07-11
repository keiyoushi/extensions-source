plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangahere"
    versionCode = 23
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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
