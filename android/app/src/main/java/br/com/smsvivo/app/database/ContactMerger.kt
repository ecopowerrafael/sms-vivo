package br.com.smsvivo.app.database

data class ContactPatch(
    val name: String,
    val phone: String,
    val normalizedPhone: String,
    val company: String?, val companyProvided: Boolean,
    val city: String?, val cityProvided: Boolean,
    val state: String?, val stateProvided: Boolean,
    val customFieldsJson: String?, val customFieldsProvided: Boolean,
    val authorized: Boolean?, val authorizedProvided: Boolean,
    val unsubscribed: Boolean?, val unsubscribedProvided: Boolean,
)

fun mergeContact(existing: ContactEntity?, patch: ContactPatch): ContactEntity = ContactEntity(
    id = existing?.id ?: 0,
    name = patch.name,
    phone = patch.phone,
    normalizedPhone = patch.normalizedPhone,
    company = if (patch.companyProvided) patch.company else existing?.company,
    city = if (patch.cityProvided) patch.city else existing?.city,
    state = if (patch.stateProvided) patch.state else existing?.state,
    customFieldsJson = if (patch.customFieldsProvided) patch.customFieldsJson else existing?.customFieldsJson,
    authorized = if (patch.authorizedProvided) patch.authorized ?: false else existing?.authorized ?: false,
    unsubscribed = if (patch.unsubscribedProvided) patch.unsubscribed ?: false else existing?.unsubscribed ?: false,
    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
)
