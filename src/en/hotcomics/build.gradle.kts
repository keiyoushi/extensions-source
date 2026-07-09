plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HotComics"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hotcomics.me"
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}
