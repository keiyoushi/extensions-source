plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nude-Moon"
    versionCode = 29
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        baseUrl {
            custom("https://nude-moon.org")
        }
        lang = "ru"
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}
