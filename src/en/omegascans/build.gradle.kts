import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Omega Scans"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "heancms"

    source {
        lang = "en"
        baseUrl = "https://omegascans.org"
        // Site changed from MangaThemesia to HeanCms.
        versionId = 2
    }
}
