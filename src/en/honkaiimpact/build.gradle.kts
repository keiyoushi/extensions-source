import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HonkaiImpact3"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Honkai Impact 3rd"
        lang = "en"
        baseUrl = "https://manga.honkaiimpact3.com"
    }
}
