import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kuaikanmanhua"
    versionCode = 12
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "快看漫画"
        lang = "zh-Hans"
        baseUrl = "https://www.kuaikanmanhua.com"
        id = 8099870292642776005L
    }

    deeplink {
        host("kuaikanmanhua.com")
        host("*.kuaikanmanhua.com")
        path("/mobile/..*")
        path("/web/topic/..*")
    }
}
