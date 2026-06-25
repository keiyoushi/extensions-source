plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Stop"
    className = "MangaStop"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://mangastop.net"
}

dependencies {

    api(project(":lib:cookieinterceptor"))
    implementation(project(":lib:randomua"))
}
