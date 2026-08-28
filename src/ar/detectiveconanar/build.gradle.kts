import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Detective Conan Ar"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        name = "شبكة كونان العربية"
        lang = "ar"
        baseUrl = "https://manga.detectiveconanar.com"
    }
}
