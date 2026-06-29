plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDex"
    className = "MangaDexFactory"
    versionCode = 210
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("mangadex.org")
        host("canary.mangadex.dev")
        path("/title/..*")
        path("/manga/..*")
        path("/chapter/..*")
        path("/group/..*")
        path("/author/..*")
        path("/user/..*")
        path("/list/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
