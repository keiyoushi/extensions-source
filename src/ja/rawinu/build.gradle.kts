plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawINU"
    className = "RawINU"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "fmreader"
    baseUrl = "https://rawinu.com"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
