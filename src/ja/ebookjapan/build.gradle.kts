plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "eBookJapan"
    className = "EbookJapan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
