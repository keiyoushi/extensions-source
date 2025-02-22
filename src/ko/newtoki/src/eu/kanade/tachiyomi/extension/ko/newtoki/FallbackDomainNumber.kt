package eu.kanade.tachiyomi.extension.ko.newtoki
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

/**
 * This value will be automatically overwritten when building the extension.
 * After building, this file will be restored.
 *
 * Even if this value is built into the extension, the network call will fail
 * because of underscore character and the extension will update it on its own.
 */
const val fallbackDomainNumber = "_failed_to_fetch_domain_number"
