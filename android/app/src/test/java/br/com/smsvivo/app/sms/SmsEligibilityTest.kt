package br.com.smsvivo.app.sms

import br.com.smsvivo.app.database.ContactEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsEligibilityTest {
    @Test fun bridgeReadsJavascriptIntegerAsLong() {
        val inspection = BridgeArgumentReader.inspectValue(true, 60000)
        assertTrue(inspection.present)
        assertEquals("java.lang.Integer", inspection.rawType)
        assertEquals(60000L, inspection.converted)
    }

    @Test fun bridgeReadsNumericStringAsLong() {
        val inspection = BridgeArgumentReader.inspectValue(true, "60000")
        assertTrue(inspection.present)
        assertEquals("java.lang.String", inspection.rawType)
        assertEquals(60000L, inspection.converted)
    }

    @Test fun bridgeDistinguishesMissingValue() {
        val inspection = BridgeArgumentReader.inspectValue(false, null)
        assertFalse(inspection.present)
        assertEquals(null, inspection.converted)
    }

    @Test fun authorizedLandlineIsRejected() {
        assertFalse(SmsEligibility.isEligible(ContactEntity(name = "Fixo", phone = "1133334444", normalizedPhone = "+551133334444", authorized = true)))
    }

    @Test fun authorizedCompleteMobileIsAccepted() {
        assertTrue(SmsEligibility.isEligible(ContactEntity(name = "Móvel", phone = "11999990000", normalizedPhone = "+5511999990000", authorized = true)))
    }

    @Test fun unsubscribedMobileIsRejected() {
        assertFalse(SmsEligibility.isEligible(ContactEntity(name = "Opt-out", phone = "11999990000", normalizedPhone = "+5511999990000", authorized = true, unsubscribed = true)))
    }

    @Test fun normalizesBrazilianMobileRepresentationsToTheSameKey() {
        assertEquals("+5515997813981", SmsEligibility.normalizeBrazilian("(15) 99781-3981"))
        assertEquals("+5515997813981", SmsEligibility.normalizeBrazilian("15997813981"))
        assertEquals("+5515997813981", SmsEligibility.normalizeBrazilian("+55 15 99781-3981"))
        assertEquals(SmsEligibility.MOBILE_VALID, SmsEligibility.classify("5515997813981"))
    }

    @Test fun classifiesFixedLegacyAndAmbiguousNumbersBeforeImport() {
        assertEquals(SmsEligibility.LANDLINE, SmsEligibility.classify("1133334444"))
        assertEquals(SmsEligibility.MOBILE_LEGACY_CANDIDATE, SmsEligibility.classify("1188887777"))
        assertEquals(SmsEligibility.AMBIGUOUS, SmsEligibility.classify("1100000000"))
        assertEquals(SmsEligibility.INVALID, SmsEligibility.classify("000"))
    }
}
