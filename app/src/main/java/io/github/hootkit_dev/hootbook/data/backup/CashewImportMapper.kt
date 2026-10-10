package io.github.hootkit_dev.hootbook.data.backup

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

object CashewImportMapper {

    /**
     * Parses Cashew's date format (epoch seconds/milliseconds or ISO-8601 strings) into LocalDateTime.
     */
    fun toLocalDateTime(dateObj: Any?): LocalDateTime {
        if (dateObj == null) return LocalDateTime.now()
        return when (dateObj) {
            is Long -> {
                val epochMs = if (dateObj < 100000000000L) dateObj * 1000 else dateObj
                LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
            }
            is Double -> {
                val epochMs = if (dateObj < 100000000000.0) (dateObj * 1000).toLong() else dateObj.toLong()
                LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
            }
            is String -> {
                val cleanStr = dateObj.trim()
                if (cleanStr.isEmpty()) return LocalDateTime.now()
                try {
                    // Try parsing as ISO-8601 string, e.g. "2023-10-27T15:30:00Z"
                    val instant = Instant.parse(cleanStr)
                    LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                } catch (e: Exception) {
                    try {
                        // Fallback: standard local date-time parser (replace space with T for ISO format)
                        LocalDateTime.parse(cleanStr.replace(" ", "T"))
                    } catch (ex: Exception) {
                        try {
                            // Try parsing as raw epoch Long string
                            val longVal = cleanStr.toLong()
                            val epochMs = if (longVal < 100000000000L) longVal * 1000 else longVal
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                        } catch (ey: Exception) {
                            LocalDateTime.now()
                        }
                    }
                }
            }
            else -> LocalDateTime.now()
        }
    }

    /**
     * Converts a Double/REAL amount in major units to positive BigDecimal scaled to 2 decimal places.
     */
    fun toBigDecimal(amount: Double): BigDecimal {
        return BigDecimal.valueOf(amount).abs().setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Normalizes colors (decimal integer ARGB, hex ARGB, or standard hex) to a standard #RRGGBB hex string.
     */
    fun normalizeColor(colorVal: Any?): String {
        if (colorVal == null) return "#4CAF50"
        val colorStr = colorVal.toString().trim()
        if (colorStr.isEmpty()) return "#4CAF50"

        val cleanHex = if (colorStr.startsWith("#")) colorStr.substring(1) else colorStr

        return try {
            val longVal = cleanHex.toLongOrNull() ?: cleanHex.toLongOrNull(16)
            if (longVal != null) {
                val hexStr = String.format("%08X", longVal)
                if (hexStr.length >= 6) {
                    "#" + hexStr.takeLast(6)
                } else {
                    "#" + hexStr.padStart(6, '0')
                }
            } else {
                if (cleanHex.length >= 6) {
                    "#" + cleanHex.takeLast(6)
                } else {
                    "#" + cleanHex.padStart(6, '0')
                }
            }
        } catch (e: Exception) {
            "#4CAF50"
        }
    }

    /**
     * Derives a stable 4-character account placeholder based on the SHA-1 hash of the wallet name.
     */
    fun deriveLast4(walletName: String): String {
        if (walletName.isEmpty()) return "0000"
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(walletName.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            hex.take(4).uppercase(Locale.ROOT)
        } catch (e: Exception) {
            "0000"
        }
    }
}
