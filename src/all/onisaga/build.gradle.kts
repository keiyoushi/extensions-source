plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OniSaga"
    className = "OniSagaFactory"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("onisaga.com")
        path("/manga/..*")
        path("/read/..*")
    }
}
