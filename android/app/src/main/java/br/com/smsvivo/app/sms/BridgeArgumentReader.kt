package br.com.smsvivo.app.sms

import org.json.JSONObject

data class LongArgumentInspection(
    val present: Boolean,
    val rawType: String,
    val rawValue: String?,
    val converted: Long?,
)

object BridgeArgumentReader {
    fun inspect(data: JSONObject, name: String): LongArgumentInspection {
        val present = data.has(name)
        val raw = data.opt(name).takeUnless { it == null || it == JSONObject.NULL }
        val converted = when (raw) {
            is Number -> raw.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 }?.let { raw.toLong() }
            is String -> raw.trim().toLongOrNull()
            else -> null
        }
        return LongArgumentInspection(
            present = present,
            rawType = raw?.javaClass?.name ?: "absent",
            rawValue = when (raw) {
                is Number, is String -> raw.toString()
                null -> null
                else -> "<unsupported>"
            },
            converted = converted,
        )
    }

    fun readLong(data: JSONObject, name: String): Long? = inspect(data, name).converted
}
