import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lelscan-VF"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "fuzzydoodle"

    source {
        lang = "fr"
        baseUrl = "https://lelscanfr.com"
        // mmrcms -> FuzzyDoodle
        versionId = 2
    }
}
