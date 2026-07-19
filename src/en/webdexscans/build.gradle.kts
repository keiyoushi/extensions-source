import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webdex Scans"
    versionCode = 53
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://webdexscans.com"
        // Hardcode versionId to force users to migrate their old Madara entries.
        versionId = 2
    }

    deeplink {
        path("/series/..*")
    }
}
