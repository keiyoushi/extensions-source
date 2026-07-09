plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lua Scans"
    versionCode = 20
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "heancms"

    source {
        lang = "en"
        baseUrl = "https://luacomic.org"
        // Moved from Keyoapp to HeanCms
        versionId = 3
    }
}
