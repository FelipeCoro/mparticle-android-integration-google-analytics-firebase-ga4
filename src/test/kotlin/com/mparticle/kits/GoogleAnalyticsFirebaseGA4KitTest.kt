package com.mparticle.kits

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.google.firebase.analytics.FirebaseAnalytics
import com.mparticle.MPEvent
import com.mparticle.MParticle
import com.mparticle.MParticleOptions.DataplanOptions
import com.mparticle.commerce.CommerceEvent
import com.mparticle.commerce.Product
import com.mparticle.commerce.Promotion
import com.mparticle.commerce.TransactionAttributes
import com.mparticle.identity.IdentityApi
import com.mparticle.internal.CoreCallbacks
import com.mparticle.internal.CoreCallbacks.KitListener
import com.mparticle.testutils.TestingUtils
import junit.framework.TestCase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class GoogleAnalyticsFirebaseGA4KitTest {
    private lateinit var kitInstance: GoogleAnalyticsFirebaseGA4Kit
    private lateinit var firebaseSdk: FirebaseAnalytics
    private var random = Random()

    @Before
    @Throws(JSONException::class)
    fun before() {
        FirebaseAnalytics.clearInstance()
        FirebaseAnalytics.setFirebaseId("firebaseId")
        kitInstance = GoogleAnalyticsFirebaseGA4Kit()
        MParticle.setInstance(Mockito.mock(MParticle::class.java))
        Mockito.`when`(MParticle.getInstance()?.Identity()).thenReturn(
            Mockito.mock(
                IdentityApi::class.java
            )
        )
        val kitManager = KitManagerImpl(
            Mockito.mock(
                Context::class.java
            ), null, emptyCoreCallbacks, null
        )
        kitInstance.kitManager = kitManager
        kitInstance.configuration =
            KitConfiguration.createKitConfiguration(JSONObject().put("id", "-1"))
        kitInstance.onKitCreate(HashMap(), Mockito.mock(Context::class.java))
        firebaseSdk = FirebaseAnalytics.getInstance(null)!!

    }

    /**
     * make sure that all MPEvents are getting translating their getInfo() value to the bundle of the Firebase event.
     * MPEvent.getName() should be the firebase event name in all cases, except when the MPEvent.type is MPEvent.Search
     */
    @Test
    fun testEmptyEvent() {
        kitInstance.logEvent(MPEvent.Builder("eventName", MParticle.EventType.Other).build())
        TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
        var firebaseEvent = firebaseSdk.loggedEvents[0]
        TestCase.assertEquals("eventName", firebaseEvent.key)
        TestCase.assertEquals(0, firebaseEvent.value.size())

        for (i in 0..9) {
            val event = TestingUtils.getInstance().randomMPEventRich
            firebaseSdk.clearLoggedEvents()
            kitInstance.logEvent(event)
            TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
            firebaseEvent = firebaseSdk.loggedEvents[0]
            if (event.eventType != MParticle.EventType.Search) {
                TestCase.assertEquals(
                    kitInstance.standardizeName(event.eventName, true),
                    firebaseEvent.key
                )

            } else {
                TestCase.assertEquals("search", firebaseEvent.key)
            }
            event.customAttributeStrings?.let {
                TestCase.assertEquals(
                    it.size,
                    firebaseEvent.value.size()
                )
                for (customAttEvent in it) {
                    val key = kitInstance.standardizeName(customAttEvent.key, true)
                    val value = kitInstance.standardizeValue(customAttEvent.value, true)
                    if (key != null) {
                        TestCase.assertEquals(
                            value, firebaseEvent.value.getString(
                                key
                            )
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testPromotionCommerceEvent() {
        val promotion = Promotion()
        promotion.creative = "asdva"
        promotion.id = "1234"
        promotion.name = "1234asvd"
        promotion.position = "2"
        val event = CommerceEvent.Builder(Promotion.CLICK, promotion).build()
        kitInstance.logEvent(event)
        TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
    }

    @Test
    fun testShippingInfoCommerceEvent() {
        val event = CommerceEvent.Builder(
            Product.CHECKOUT_OPTION,
            Product.Builder("asdv", "asdv", 1.3).build()
        )
            .addCustomFlag(
                GoogleAnalyticsFirebaseGA4Kit.CF_GA4COMMERCE_EVENT_TYPE,
                FirebaseAnalytics.Event.ADD_SHIPPING_INFO
            )
            .addCustomFlag(GoogleAnalyticsFirebaseGA4Kit.CF_GA4_SHIPPING_TIER, "overnight")
            .build()
        kitInstance.logEvent(event)
        TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
        TestCase.assertEquals("add_shipping_info", firebaseSdk.loggedEvents[0].key)
        TestCase.assertEquals(
            "overnight",
            firebaseSdk.loggedEvents[0].value.getString("shipping_tier")
        )
    }

    @Test
    fun testPaymentInfoCommerceEvent() {
        val event = CommerceEvent.Builder(
            Product.CHECKOUT_OPTION,
            Product.Builder("asdv", "asdv", 1.3).build()
        )
            .addCustomFlag(
                GoogleAnalyticsFirebaseGA4Kit.CF_GA4COMMERCE_EVENT_TYPE,
                FirebaseAnalytics.Event.ADD_PAYMENT_INFO
            )
            .addCustomFlag(GoogleAnalyticsFirebaseGA4Kit.CF_GA4_PAYMENT_TYPE, "visa")
            .build()
        kitInstance.logEvent(event)
        TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
        TestCase.assertEquals("add_payment_info", firebaseSdk.loggedEvents[0].key)
        TestCase.assertEquals("visa", firebaseSdk.loggedEvents[0].value.getString("payment_type"))
    }

    @Test
    fun testCheckoutOptionCommerceEvent() {
        val customEventTypes = arrayOf(
            FirebaseAnalytics.Event.ADD_PAYMENT_INFO,
            FirebaseAnalytics.Event.ADD_SHIPPING_INFO
        )
        for (customEventType in customEventTypes) {
            val event = CommerceEvent.Builder(
                Product.CHECKOUT_OPTION,
                Product.Builder("asdv", "asdv", 1.3).build()
            )
                .addCustomFlag(
                    GoogleAnalyticsFirebaseGA4Kit.CF_GA4COMMERCE_EVENT_TYPE,
                    customEventType
                )
                .build()
            kitInstance.logEvent(event)
            TestCase.assertEquals(1, firebaseSdk.loggedEvents.size)
            TestCase.assertEquals(customEventType, firebaseSdk.loggedEvents[0].key)
            firebaseSdk.clearLoggedEvents()
        }
    }

    @Test
    @Throws(IllegalAccessException::class)
    fun testCommerceEvent() {
        for (field in Product::class.java.fields) {
            if (Modifier.isPublic(field.modifiers) && Modifier.isStatic(field.modifiers)) {
                firebaseSdk.clearLoggedEvents()
                val eventType = field?.get(null).toString()
                if (eventType != "remove_from_wishlist" && eventType != "checkout_option") {
                    val event = CommerceEvent.Builder(
                        eventType,
                        Product.Builder("asdv", "asdv", 1.3).build()
                    )
                        .transactionAttributes(
                            TransactionAttributes().setId("235").setRevenue(23.3)
                                .setAffiliation("231")
                        )
                        .build()
                    kitInstance.logEvent(event)
                    TestCase.assertEquals(
                        "failed for event type: $eventType",
                        1,
                        firebaseSdk.loggedEvents.size
                    )
                }
            }
        }
    }

    @Test
    fun testNameStandardization() {
        val badPrefixes = arrayOf("firebase_event_name", "google_event_name", "ga_event_name")
        for (badPrefix in badPrefixes) {
            val clean = kitInstance.standardizeName(badPrefix, random.nextBoolean())
            TestCase.assertEquals("event_name", clean)
        }
        val emptySpace1 = "event name"
        val emptySpace2 = "event_name "
        val emptySpace3 = "event  name "
        val emptySpace4 = "event - name "
        TestCase.assertEquals(
            "event_name",
            kitInstance.standardizeName(emptySpace1, random.nextBoolean())
        )
        TestCase.assertEquals(
            "event_name_",
            kitInstance.standardizeName(emptySpace2, random.nextBoolean())
        )
        TestCase.assertEquals(
            "event_name_",
            kitInstance.standardizeName(emptySpace3, random.nextBoolean())
        )
        TestCase.assertEquals(
            "event_name_",
            kitInstance.standardizeName(emptySpace4, random.nextBoolean())
        )
        val badStarts = arrayOf(
            "!@#$%^&*()_+=[]{}|'\"?><:;event_name",
            "_event_name",
            "   event_name",
            "_event_name"
        )
        for (badStart in badStarts) {
            val clean = kitInstance.standardizeName(badStart, random.nextBoolean())
            TestCase.assertEquals("event_name", clean)
        }
        val tooLong =
            "abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890"
        var sanitized: String = kitInstance.standardizeName(tooLong, true).toString()
        TestCase.assertEquals(40, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))
        sanitized = kitInstance.standardizeName(tooLong, false).toString()
        TestCase.assertEquals(24, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))
        sanitized = kitInstance.standardizeValue(tooLong, true)
        TestCase.assertEquals(100, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))
        sanitized = kitInstance.standardizeValue(tooLong, false)
        TestCase.assertEquals(36, sanitized.length)
        TestCase.assertTrue(tooLong.startsWith(sanitized))

    }

    @Test
    fun testScreenNameSanitized() {
        kitInstance.logScreen("Some long Screen name", null)
        TestCase.assertEquals(
            "Some_long_Screen_name",
            FirebaseAnalytics.getInstance(null)?.currentScreenName
        )
    }

    private var emptyCoreCallbacks: CoreCallbacks = object : CoreCallbacks {
        var activity = Activity()
        override fun isBackgrounded(): Boolean = false

        override fun getUserBucket(): Int = 0

        override fun isEnabled(): Boolean = false

        override fun setIntegrationAttributes(i: Int, map: Map<String, String>) {}

        override fun getIntegrationAttributes(i: Int): Map<String, String>? = null

        override fun getCurrentActivity(): WeakReference<Activity> = WeakReference(activity)

        override fun getLatestKitConfiguration(): JSONArray? = null

        override fun getDataplanOptions(): DataplanOptions? = null

        override fun isPushEnabled(): Boolean = false

        override fun getPushSenderId(): String? = null

        override fun getPushInstanceId(): String? = null

        override fun getLaunchUri(): Uri? = null

        override fun getLaunchAction(): String? = null

        override fun getKitListener(): KitListener = KitListener.EMPTY

    }
}
