package io.github.hootkit_dev.hootbook.utils

import java.math.BigDecimal

/**
 * Safely computes the sum of BigDecimals without any overload resolution ambiguities
 * across different Kotlin compiler targets or Compose dependencies.
 */
inline fun <T> Iterable<T>.sumOfBigDecimal(selector: (T) -> BigDecimal): BigDecimal {
    var sum: BigDecimal = BigDecimal.ZERO
    for (element in this) {
        sum = sum.add(selector(element))
    }
    return sum
}

/**
 * Safely computes the sum of Doubles without any overload resolution ambiguities.
 */
inline fun <T> Iterable<T>.sumOfDouble(selector: (T) -> Double): Double {
    var sum: Double = 0.0
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

/**
 * Safely finds the maximum Double value or null without any overload resolution ambiguities.
 */
inline fun <T> Iterable<T>.maxDoubleOrNull(selector: (T) -> Double): Double? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    var max = selector(iterator.next())
    while (iterator.hasNext()) {
        val v = selector(iterator.next())
        if (max < v) {
            max = v
        }
    }
    return max
}
