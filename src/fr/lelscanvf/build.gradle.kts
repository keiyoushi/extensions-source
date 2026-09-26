import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lelscan-VF"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "fuzzydoodle"

    source {
        lang = "fr"
        baseUrl = "https://www.lelscanfr.com"
        // mmrcms -> FuzzyDoodle
        versionId = 2
    }
}
