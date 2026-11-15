import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jisu Manhua"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "dm5"

    source {
        lang = "zh"
        name = "极速漫画"
        baseUrl = "https://www.1kkk.com"
    }

    deeplink {
        host("www.1kkk.com")
        host("m.1kkk.com")
        path("/manhua..*")
        path("/ch..*")
        path("/vol..*")
        path("/other..*")
    }
}
