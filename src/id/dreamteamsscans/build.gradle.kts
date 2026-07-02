plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DreamTeams Scans"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://dreamteams.space"
    }

    deeplink {
        host("dreamteams.space")
        path("/comic/..*")
    }
}
