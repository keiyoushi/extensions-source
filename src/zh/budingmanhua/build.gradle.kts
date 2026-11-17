import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Buding Manhua"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "布丁漫画"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://hxy4.com",
                "https://qimeitj.com",
                "https://hxy10.com",
            )
        }
    }

    deeplink {
        path("/manhua/..*")
        path("/chapter/..*")
    }
}
