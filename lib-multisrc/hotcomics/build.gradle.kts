plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:cookieinterceptor"))
}

keiyoushi {
    baseVersionCode = 2
    libVersion = "1.4"
}
