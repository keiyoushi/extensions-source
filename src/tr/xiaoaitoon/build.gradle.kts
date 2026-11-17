import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xiaoaitoon"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://xiaoaitoon.org.tr"
    }

    deeplink {
        host("xiaoaitoon.org.tr")
        host("www.xiaoaitoon.org.tr")
        path("/manga/..*")
        path("/manga-genre/..*")
        path("/manga-author/..*")
        path("/manga-artist/..*")
    }
}
