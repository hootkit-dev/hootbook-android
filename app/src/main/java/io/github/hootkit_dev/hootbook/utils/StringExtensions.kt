package io.github.hootkit_dev.hootbook.utils

/**
 * Safely capitalizes the first character of the string without any overload resolution ambiguities
 * across different Kotlin compiler targets.
 */
fun String.capitalizeFirst(): String {
    if (this.isEmpty()) return this
    return this[0].uppercaseChar() + this.substring(1)
}

/**
 * Safely converts the first character of the string to title case without any overload resolution ambiguities.
 */
fun String.titlecaseFirst(): String {
    if (this.isEmpty()) return this
    return this[0].titlecaseChar() + this.substring(1)
}
