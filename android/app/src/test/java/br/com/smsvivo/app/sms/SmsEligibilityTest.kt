package br.com.smsvivo.app.sms

import br.com.smsvivo.app.database.ContactEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class SmsEligibilityTest {
    @Test fun bridgeReadsJavascriptIntegerAsLong() {
        val inspection = BridgeArgumentReader.inspect(JSONObject().put("intervalMs", 60000), "intervalMs")
        assertTrue(inspection.present)
        assertEquals("java.lang.Integer", inspection.rawType)
        assertEquals(60000L, inspection.converted)
    }

    @Test fun bridgeReadsNumericStringAsLong() {
        val inspection = BridgeArgumentReader.inspect(JSONObject().put("intervalMs", "60000"), "intervalMs")
        assertTrue(inspection.present)
        assertEquals("java.lang.String", inspection.rawType)
        assertEquals(60000L, inspection.converted)
    }

    @Test fun bridgeDistinguishesMissingValue() {
        val inspection = BridgeArgumentReader.inspect(JSONObject(), "intervalMs")
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
}
