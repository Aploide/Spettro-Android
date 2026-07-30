package to.eyed.spettro.mobile.acp

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpAccountStatus
import to.eyed.spettro.mobile.core.acp.AcpContentBlock
import to.eyed.spettro.mobile.core.acp.AcpLoginStatus
import to.eyed.spettro.mobile.core.acp.AcpModelsList
import to.eyed.spettro.mobile.core.acp.AcpProvidersList
import to.eyed.spettro.mobile.core.acp.SubscriptionPlan

private fun el(text: String) = SpettroJson.parseToJsonElement(text)

class AcpExtensionsTest {

    @Test
    fun accountStatusDerivations() {
        val status = AcpAccountStatus.parse(
            el(
                """{"signedIn":true,"email":"dev@example.com","plan":"","creditsUsed":25.0,
                    "creditLimit":100.0,"remainingCredits":80.0,"modelCount":12,
                    "login":{"loginId":"L1","status":"pending","browserUrl":"https://x"}}"""
            )
        )
        assertTrue(status.signedIn)
        // Signed in with a blank plan => free tier.
        assertEquals("free", status.effectivePlan)
        assertEquals(SubscriptionPlan.FREE, status.subscriptionPlan)
        // remainingCredits preferred over limit - used (would be 0.75).
        assertEquals(0.8, status.remainingFraction!!, 1e-9)
        assertEquals(AcpLoginStatus.State.PENDING, status.login!!.state)
    }

    @Test
    fun accountStatusSignedOutHasNoPlan() {
        val status = AcpAccountStatus.parse(el("""{"signedIn":false,"plan":"pro"}"""))
        assertEquals("", status.effectivePlan)
        assertNull(status.subscriptionPlan)
        assertNull(status.remainingFraction)
    }

    @Test
    fun remainingFractionFallsBackToLimitMinusUsed() {
        val status = AcpAccountStatus.parse(
            el("""{"signedIn":true,"plan":"max","creditsUsed":30.0,"creditLimit":120.0}""")
        )
        assertEquals(SubscriptionPlan.MAX, status.subscriptionPlan)
        assertEquals(0.75, status.remainingFraction!!, 1e-9)
    }

    @Test
    fun providersListCountsConnections() {
        val list = AcpProvidersList.parse(
            el(
                """{"providers":[
                     {"id":"openai","name":"OpenAI","connected":true,"modelCount":4},
                     {"id":"groq","name":"Groq","connected":false}],
                    "local":[{"endpoint":"http://localhost:1234","name":"LM Studio"}],
                    "subscription":{"id":"spettro","name":"Spettro","connected":true}}"""
            )
        )
        assertEquals(3, list.connectedCount)
        assertEquals("localhost:1234", list.local[0].shortHost)
        assertTrue(!list.isEmpty)
    }

    @Test
    fun modelsListGroupsFavoritesFirst() {
        val list = AcpModelsList.parse(
            el(
                """{"models":[
                     {"provider":"openai","providerName":"OpenAI","name":"b-model","context":128000},
                     {"provider":"openai","providerName":"OpenAI","name":"a-model","favorite":true,"context":1000000},
                     {"provider":"local","providerName":"LM Studio","name":"tiny","local":true,"context":512}],
                    "activeProvider":"openai","activeModel":"a-model"}"""
            )
        )
        val groups = list.grouped
        assertEquals(listOf("OpenAI", "LM Studio"), groups.map { it.provider })
        assertEquals(listOf("a-model", "b-model"), groups[0].models.map { it.name })
        assertEquals("1.0M", groups[0].models[0].contextLabel)
        assertEquals("128k", groups[0].models[1].contextLabel)
        assertEquals("512", groups[1].models[0].contextLabel)
        assertEquals("openai:a-model", groups[0].models[0].id)
    }

    @Test
    fun contentBlockRoundTrip() {
        val text = AcpContentBlock.Text("hello")
        assertEquals(text, AcpContentBlock.parse(text.toJson()))
        val image = AcpContentBlock.Image(data = "aGVsbG8=", mimeType = "image/jpeg")
        assertEquals(image, AcpContentBlock.parse(image.toJson()))
        assertNull(AcpContentBlock.parse(el("""{"type":"audio","data":"x"}""").jsonObject))
    }
}
