import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CrunchyScan (unoriginal)"
    pkgName = "fr.crunchyscan"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "crunchyscanst"

    source {
        lang = "fr"
        baseUrl = "https://crunchyscan.st"
        id = 5122789490740525400L
    }
}
