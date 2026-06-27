plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DreamTeams Scans"
    className = "DreamTeamsScans"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    baseUrl = "https://dreamteams.space"

    deeplink {
        host("dreamteams.space")
        path("/comic/..*")
    }
}
