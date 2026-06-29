plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comicaso"
    className = "Comicaso"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    deeplink {
        host("v3.comicaso.pro")
        path("/..*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
