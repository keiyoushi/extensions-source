plugins {
    id("com.android.library") // أضف هذا السطر
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 51
    libVersion = "1.4"

    deeplink {
        path("/.*/..*")
    }
}
