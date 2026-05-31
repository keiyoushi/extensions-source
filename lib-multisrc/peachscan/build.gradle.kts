import keiyoushi.gradle.extensions.baseVersionCode

plugins {
    alias(kei.plugins.multisrc)
}

baseVersionCode = 10

dependencies {
    api(project(":lib:zipinterceptor"))
}
