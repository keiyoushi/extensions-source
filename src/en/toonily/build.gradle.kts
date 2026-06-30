plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonily"
    versionCode = 14
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://toonily.com"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
