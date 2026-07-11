plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangago"
    versionCode = 36
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.mangago.me"
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}
