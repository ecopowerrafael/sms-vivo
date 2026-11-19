package br.com.smsvivo.app.sms

import br.com.smsvivo.app.database.ContactEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsEligibilityTest {
    @Test fun authorizedLandlineIsRejected() {
        assertFalse(SmsEligibility.isEligible(ContactEntity(name = "Fixo", phone = "1133334444", normalizedPhone = "+551133334444", authorized = true)))
    }

    @Test fun authorizedCompleteMobileIsAccepted() {
        assertTrue(SmsEligibility.isEligible(ContactEntity(name = "Móvel", phone = "11999990000", normalizedPhone = "+5511999990000", authorized = true)))
    }

    @Test fun unsubscribedMobileIsRejected() {
        assertFalse(SmsEligibility.isEligible(ContactEntity(name = "Opt-out", phone = "11999990000", normalizedPhone = "+5511999990000", authorized = true, unsubscribed = true)))
    }
}
