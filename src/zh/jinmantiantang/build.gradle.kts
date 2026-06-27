plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jinman Tiantang"
    className = "Jinmantiantang"
    versionCode = 57
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("18comic.vip")
        host("18comic.ink")
        host("jmcomic-zzz.one")
        host("jmcomic-zzz.org")
        path("/album/..*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
