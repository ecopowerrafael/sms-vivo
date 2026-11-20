package br.com.smsvivo.app.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueuePersistenceTest {
    private lateinit var database: SmsDatabase
    @Before fun setUp() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SmsDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() { database.close() }
    @Test fun queueItemPersistsCampaignAssociation() {
        val campaignId = database.campaigns().insert(CampaignEntity(name = "Campanha", subscriptionId = 1, intervalMs = 60000L))
        assertEquals(60000L, database.campaigns().find(campaignId)?.intervalMs)
        database.queue().insert(QueueItemEntity(requestId = "campaign-request", campaignId = campaignId, destination = "+5511999999999", body = "teste", subscriptionId = 1, requestIntervalMs = 60000L))
        assertEquals(campaignId, database.queue().byRequestId("campaign-request")?.campaignId)
        assertEquals(60000L, database.queue().byRequestId("campaign-request")?.requestIntervalMs)
    }
    @Test fun multipartCallbacksAreCountedOnceAndNeedAllSentPartsBeforeDelivery() {
        database.queue().insert(QueueItemEntity(requestId = "multipart-request", destination = "+5511999999999", body = "teste", subscriptionId = 1, state = QueueStates.PROCESSING, partsExpected = 2))
        val first = SmsPartCallbackEntity("multipart-request", 0, SmsResultPhase.SENT, -1, 10)
        assertNotNull(database.recordPartCallback(first))
        assertEquals(1, database.queue().byRequestId("multipart-request")?.sentParts)
        assertEquals(null, database.recordPartCallback(first))
        assertEquals(1, database.queue().byRequestId("multipart-request")?.sentParts)
        database.recordPartCallback(SmsPartCallbackEntity("multipart-request", 0, SmsResultPhase.DELIVERED, -1, 11))
        assertEquals(1, database.queue().byRequestId("multipart-request")?.deliveredParts)
        database.recordPartCallback(SmsPartCallbackEntity("multipart-request", 1, SmsResultPhase.SENT, -1, 12))
        database.recordPartCallback(SmsPartCallbackEntity("multipart-request", 1, SmsResultPhase.DELIVERED, -1, 13))
        assertEquals(QueueStates.DELIVERED, database.queue().byRequestId("multipart-request")?.state)
    }

    @Test fun outOfOrderDeliveryDoesNotCompleteBeforeAllSendCallbacks() {
        database.queue().insert(QueueItemEntity(requestId = "out-of-order", destination = "+5511999999999", body = "teste", subscriptionId = 1, state = QueueStates.PROCESSING, partsExpected = 2))
        database.recordPartCallback(SmsPartCallbackEntity("out-of-order", 0, SmsResultPhase.DELIVERED, 0, 10))
        assertEquals(QueueStates.PROCESSING, database.queue().byRequestId("out-of-order")?.state)
        database.recordPartCallback(SmsPartCallbackEntity("out-of-order", 0, SmsResultPhase.SENT, 0, 11))
        database.recordPartCallback(SmsPartCallbackEntity("out-of-order", 1, SmsResultPhase.SENT, 0, 12))
        database.recordPartCallback(SmsPartCallbackEntity("out-of-order", 1, SmsResultPhase.DELIVERED, 0, 13))
        assertEquals(QueueStates.DELIVERED, database.queue().byRequestId("out-of-order")?.state)
    }

    @Test fun deliveryFailureIsTerminalAndDuplicateFailureIsIgnored() {
        database.queue().insert(QueueItemEntity(requestId = "delivery-failure", destination = "+5511999999999", body = "teste", subscriptionId = 1, state = QueueStates.PROCESSING, partsExpected = 1))
        val failure = SmsPartCallbackEntity("delivery-failure", 0, SmsResultPhase.DELIVERY_FAILED, 128, 20)
        assertEquals(QueueStates.FAILED, database.recordPartFailure(failure)?.state)
        assertEquals(null, database.recordPartFailure(failure))
        assertEquals(QueueStates.FAILED, database.queue().byRequestId("delivery-failure")?.state)
    }

    @Test fun recoveryOnlyMarksStaleProcessingRows() {
        database.queue().insert(QueueItemEntity(requestId = "recent", destination = "+5511999999999", body = "teste", subscriptionId = 1, state = QueueStates.PROCESSING, processingAt = 900))
        database.queue().insert(QueueItemEntity(requestId = "stale", destination = "+5511999999999", body = "teste", subscriptionId = 1, state = QueueStates.PROCESSING, processingAt = 100))
        assertEquals(1, database.queue().recoverStaleProcessing(500))
        assertEquals(QueueStates.PROCESSING, database.queue().byRequestId("recent")?.state)
        assertEquals(QueueStates.UNKNOWN, database.queue().byRequestId("stale")?.state)
    }

    @Test fun listPagingReturnsOnlyRequestedPageAndEligibleCount() {
        val listId = database.lists().insert(ContactListEntity(name = "Agenda do celular", origin = "DEVICE_CONTACTS"))
        val eligible = database.contacts().insert(ContactEntity(name = "Elegível", phone = "11999990000", normalizedPhone = "+5511999990000", phoneClassification = "MOBILE_VALID", authorized = true))
        val pending = database.contacts().insert(ContactEntity(name = "Pendente", phone = "11988887777", normalizedPhone = "+5511988887777", phoneClassification = "MOBILE_VALID", authorized = false))
        database.lists().addMember(ContactListMembershipEntity(listId, eligible))
        database.lists().addMember(ContactListMembershipEntity(listId, pending))
        assertEquals(2, database.contacts().countByList(listId, ""))
        assertEquals(1, database.contacts().countEligibleByList(listId))
        assertEquals(1, database.contacts().pageByList(listId, "", 1, 0).size)
        assertEquals(1, database.contacts().pageByList(listId, "Pendente", 50, 0).size)
    }
}
