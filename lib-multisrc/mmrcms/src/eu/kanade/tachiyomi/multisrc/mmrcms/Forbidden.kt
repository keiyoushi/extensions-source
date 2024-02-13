package eu.kanade.tachiyomi.multisrc.mmrcms

/**
 * A class similar to [kotlin.Nothing].
 *
 * This class has no instances, and is used as a placeholder
 * for hacking in forced named arguments, similar to Python's
 * `kwargs`.
 *
 * This is used instead of [kotlin.Nothing] because that class
 * is specifically forbidden from being a vararg parameter.
 */
class Forbidden private constructor()
