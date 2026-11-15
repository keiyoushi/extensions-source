import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rumanhua (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "如漫画 (unoriginal)"
        lang = "zh"
        baseUrl = "https://m.rumanhua.org"
    }

    deeplink {
        host("m.rumanhua.org")
        host("www.rumanhua.org")
        host("rumanhua.org")
        path("/news/..*")
        path("/show/..*")
    }
}
