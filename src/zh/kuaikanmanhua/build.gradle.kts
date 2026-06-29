plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kuaikanmanhua"
    className = "Kuaikanmanhua"
    versionCode = 12
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("kuaikanmanhua.com")
        host("*.kuaikanmanhua.com")
        path("/mobile/..*")
        path("/web/topic/..*")
    }
}
