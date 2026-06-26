plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaIsekaiThai"
    className = "MangaIsekaiThai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://www.mangaisekaithai.net"
}

dependencies {

    implementation(project(":lib:unpacker"))
}
