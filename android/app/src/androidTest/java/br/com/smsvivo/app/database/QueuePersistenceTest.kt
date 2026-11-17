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
        val campaignId = database.campaigns().insert(CampaignEntity(name = "Campanha", subscriptionId = 1))
        database.queue().insert(QueueItemEntity(requestId = "campaign-request", campaignId = campaignId, destination = "+5511999999999", body = "teste", subscriptionId = 1))
        assertEquals(campaignId, database.queue().byRequestId("campaign-request")?.campaignId)
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
}
