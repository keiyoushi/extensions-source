package keiyoushi.gradle.extensions

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.extra

var ExtensionAware.baseVersionCode: Int
    get() = extra.get("baseVersionCode") as Int
    set(value) = extra.set("baseVersionCode", value)

var ExtensionAware.libVersion: String
    get() = if (extra.has("libVersion")) extra.get("libVersion") as String else "1.4"
    set(value) = extra.set("libVersion", value)
