plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HotComics"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hotcomics.me"
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}
