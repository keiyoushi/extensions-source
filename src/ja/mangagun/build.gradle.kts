plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NihonKuni"
    className = "MangaGun"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "fmreader"
    baseUrl = "https://nihonkuni.com"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
