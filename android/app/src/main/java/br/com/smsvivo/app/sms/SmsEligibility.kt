package br.com.smsvivo.app.sms

import br.com.smsvivo.app.database.ContactEntity

object SmsEligibility {
    fun isCompleteMobile(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        val local = if (digits.startsWith("55") && digits.length == 13) digits.drop(2) else digits
        return Regex("^[1-9][0-9]9[0-9]{8}$").matches(local)
    }

    fun isEligible(contact: ContactEntity): Boolean = contact.authorized && !contact.unsubscribed && isCompleteMobile(contact.phone)
}
