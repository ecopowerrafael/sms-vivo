package br.com.smsmassa.app.database

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMergerTest {
    @Test fun omittedFieldsAndUnsubscribeStatusArePreserved() {
        val existing = ContactEntity(id = 7, name = "Ana", phone = "11999990000", normalizedPhone = "5511999999000", company = "Acme", city = "Campinas", state = "SP", customFieldsJson = "{\"vip\":true}", authorized = true, unsubscribed = true, createdAt = 10)
        val updated = mergeContact(existing, ContactPatch("Ana Maria", existing.phone, existing.normalizedPhone, null, false, null, false, null, false, null, false, null, false, null, false, null, false, null, false, null, false))
        assertEquals("Acme", updated.company)
        assertEquals("Campinas", updated.city)
        assertEquals("SP", updated.state)
        assertEquals("{\"vip\":true}", updated.customFieldsJson)
        assertEquals(true, updated.authorized)
        assertEquals(true, updated.unsubscribed)
        assertEquals(10, updated.createdAt)
    }

    @Test fun reimportDoesNotUndoConfirmedCorrection() {
        val existing = ContactEntity(id = 8, name = "Oficina", phone = "11987654321", normalizedPhone = "5511987654321", originalPhone = "1187654321", phoneClassification = "MOBILE_VALID", correctedAt = 20)
        val updated = mergeContact(existing, ContactPatch("Oficina atualizada", "1187654321", "551187654321", "1187654321", true, "MOBILE_LEGACY_CANDIDATE", true, null, false, null, false, null, false, null, false, null, false, null, false, null, false))
        assertEquals("11987654321", updated.phone)
        assertEquals(20L, updated.correctedAt)
    }
}
