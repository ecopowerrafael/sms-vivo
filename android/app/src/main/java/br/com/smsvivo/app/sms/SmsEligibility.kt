package br.com.smsvivo.app.sms

import br.com.smsvivo.app.database.ContactEntity

object SmsEligibility {
    const val MOBILE_VALID = "MOBILE_VALID"
    const val MOBILE_LEGACY_CANDIDATE = "MOBILE_LEGACY_CANDIDATE"
    const val LANDLINE = "LANDLINE"
    const val AMBIGUOUS = "AMBIGUOUS"
    const val INVALID = "INVALID"

    fun normalizeBrazilian(value: String): String? {
        val digits = value.filter { it.isDigit() }
        if (digits.startsWith("55") && (digits.length == 12 || digits.length == 13)) return "+$digits"
        if (digits.length == 10 || digits.length == 11) return "+55$digits"
        return null
    }

    fun classify(value: String): String {
        val normalized = normalizeBrazilian(value) ?: return INVALID
        val local = normalized.removePrefix("+55")
        if (!Regex("^[1-9][0-9][0-9]{8,9}$").matches(local)) return INVALID
        val subscriber = local.drop(2)
        return when {
            local.length == 11 && subscriber.startsWith("9") -> MOBILE_VALID
            local.length == 10 && subscriber.firstOrNull()?.let { it in '6'..'9' } == true -> MOBILE_LEGACY_CANDIDATE
            subscriber.firstOrNull()?.let { it in '2'..'5' } == true -> LANDLINE
            else -> AMBIGUOUS
        }
    }

    fun isCompleteMobile(value: String): Boolean = classify(value) == MOBILE_VALID

    fun isEligible(contact: ContactEntity): Boolean = contact.authorized && !contact.unsubscribed && isCompleteMobile(contact.phone)
}
