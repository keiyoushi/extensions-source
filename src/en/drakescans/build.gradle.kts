plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Drake Scans"
    versionCode = 16
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://drakecomic.org"
        // madara -> mangathemesia
        versionId = 2
    }
}
