plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    className = "MangaBallFactory"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("mangaball.net")
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
