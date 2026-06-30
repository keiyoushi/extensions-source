plugins {
    alias(kei.plugins.extension)
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}

keiyoushi {
    name = "Nude-Moon"
    className = "Nudemoon"
    versionCode = 29
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        baseUrl("https://nude-moon.org") {
            withCustom = true
        }
        lang = "ru"
    }
}
