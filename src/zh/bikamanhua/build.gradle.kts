import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bika Manhua (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mccms"

    source {
        name = "哔咔漫画 (unoriginal)"
        lang = "zh"
        baseUrl = "https://m.bikamanhua.com"
    }

    deeplink {
        host("m.bikamanhua.com")
        host("www.bikamanhua.com")
        path("/..*")
    }
}
