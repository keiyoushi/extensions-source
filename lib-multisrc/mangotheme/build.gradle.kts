import keiyoushi.gradle.extensions.baseVersionCode

plugins {
    alias(kei.plugins.multisrc)
}

baseVersionCode = 1

dependencies {
    api(project(":lib:cookieinterceptor"))
}
