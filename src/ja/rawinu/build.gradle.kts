plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawINU"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://rawinu.com"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
