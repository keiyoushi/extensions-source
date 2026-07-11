plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DMM/FANZA"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "DMM"
        lang = "ja"
        baseUrl = "https://book.dmm.com"
    }

    source {
        name = "FANZA"
        lang = "ja"
        baseUrl = "https://book.dmm.co.jp"
    }
}

dependencies {
    implementation(project(":lib:publus"))
    implementation(project(":lib:cookieinterceptor"))
}
