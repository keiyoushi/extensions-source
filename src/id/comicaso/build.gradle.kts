plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comicaso"
    className = "Comicaso"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("v3.comicaso.pro")
        path("/..*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
