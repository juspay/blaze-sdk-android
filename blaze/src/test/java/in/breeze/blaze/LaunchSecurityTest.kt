package `in`.breeze.blaze

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchSecurityTest {

  private val scripts = mutableListOf<String>()
  private val callbacks = mutableListOf<JSONObject>()
  private val results = mutableListOf<BlazeLaunchResult>()
  private lateinit var activity: Activity
  private lateinit var blaze: Blaze
  private lateinit var blazeWebView: BlazeWebView

  @Before
  fun setUp() {
    ShadowLog.clear()
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    blaze = Blaze()
    blaze.createWebView = { context, initiatePayload, callbackFn ->
      BlazeWebView(
        context,
        initiatePayload,
        callbackFn,
        resolveFrameUrl = { _, _, _, callback -> callback(null) },
        runScript = { _, script -> scripts.add(script) }
      ).also { blazeWebView = it }
    }
  }

  @Test
  fun requestToStringRedactsTheTokenAndDeviceBinding() {
    val request = BlazeLaunchRequest(OTT, "bt", DEVICE_BINDING, "search", "blue shoes")

    val printed = request.toString()
    assertFalse(printed.contains(OTT))
    assertFalse(printed.contains(DEVICE_BINDING))
    assertTrue(printed.contains("blue shoes"))
  }

  @Test
  fun requestFollowsTheContractFieldRules() {
    assertTrue(BlazeLaunchRequest(OTT).isValid)
    assertTrue(BlazeLaunchRequest(OTT, entryPoint = "home_banner_2").isValid)
    assertFalse(BlazeLaunchRequest("too-short").isValid)
    assertFalse(BlazeLaunchRequest("ott-0123456789-\n-abcdef").isValid)
    assertFalse(BlazeLaunchRequest("x".repeat(4097)).isValid)
    assertFalse(BlazeLaunchRequest(OTT, entryPoint = "Search Page").isValid)
    assertFalse(BlazeLaunchRequest(OTT, searchText = "a".repeat(501)).isValid)
    assertFalse(BlazeLaunchRequest(OTT, deviceBinding = "x".repeat(513)).isValid)
    assertEquals("blue shoes", BlazeLaunchRequest(OTT, searchText = "  blue shoes ").searchText)
    assertNull(BlazeLaunchRequest(OTT, searchText = "   ").searchText)
  }

  @Test
  fun launchBeforeInitiateFailsWithNotInitialised() {
    Blaze().launch(activity, BlazeLaunchRequest(OTT)) { results.add(it) }

    assertEquals(listOf(failed(BlazeLaunchError.E_NOT_INITIALISED)), results)
  }

  @Test
  fun launchOnAnotherServiceFailsWithNotInitialised() {
    blaze.initiate(activity, initiatePayload("in.breeze.onecco")) { callbacks.add(it) }

    launch()

    assertEquals(listOf(failed(BlazeLaunchError.E_NOT_INITIALISED)), results)
  }

  @Test
  fun invalidRequestFailsWithLaunchInvalidAndDeliversNothing() {
    initiateShop()

    blaze.launch(activity, BlazeLaunchRequest(OTT, entryPoint = "Not Valid")) { results.add(it) }
    appReady()

    assertEquals(listOf(failed(BlazeLaunchError.E_LAUNCH_INVALID)), results)
    assertTrue(scripts.none { it.contains(OTT) })
  }

  @Test
  fun initiateReachesJsBeforeTheLaunchAndTheOttIsDeliveredOnce() {
    initiateShop()

    launch(BlazeLaunchRequest(OTT, "bt", DEVICE_BINDING, "search", "blue shoes"))
    appReady()

    assertEquals(listOf("initiate", "process"), scripts.map { eventOf(it).getString("eventName") })
    assertEquals(1, scripts.count { it.contains(OTT) })
    val launch = eventOf(scripts.last()).getJSONObject("eventData")
    assertEquals(BlazeConstants.SHOP_SERVICE, launch.getString("service"))
    val payload = launch.getJSONObject("payload")
    assertEquals("launch", payload.getString("action"))
    assertEquals(OTT, payload.getString("ottToken"))
    assertEquals("bt", payload.getString("tenant"))
    assertEquals(DEVICE_BINDING, payload.getString("deviceBinding"))
    assertEquals("search", payload.getString("entryPoint"))
    assertEquals("blue shoes", payload.getString("searchText"))
  }

  @Test
  fun aLaunchAfterAppReadyIsDeliveredStraightAway() {
    initiateShop()
    appReady()

    launch()

    assertEquals(1, scripts.count { it.contains(OTT) })
    assertTrue(results.isEmpty())
  }

  @Test
  fun wrongPageGetsNoOttAndTheLaunchFailsWithContentLoadFailed() {
    initiateShop()
    blazeWebView.webView.loadUrl("https://evil.example/")

    launch()
    appReady()

    assertTrue(scripts.none { it.contains(OTT) })
    assertEquals(listOf(failed(BlazeLaunchError.E_CONTENT_LOAD_FAILED)), results)
  }

  @Test
  fun secondLaunchWhileAJourneyRunsFailsWithJourneyInProgress() {
    initiateShop()

    launch()
    launch()
    appReady()

    assertEquals(listOf(failed(BlazeLaunchError.E_JOURNEY_IN_PROGRESS)), results)
    assertEquals(1, scripts.count { it.contains(OTT) })
  }

  @Test
  fun reloadBeforeOstiaRepliesFailsTheLaunchAndNeverResendsTheOtt() {
    initiateShop()
    launch()
    appReady()

    appReady()

    assertEquals(listOf(failed(BlazeLaunchError.E_CONTENT_LOAD_FAILED)), results)
    assertEquals(1, scripts.count { it.contains(OTT) })
  }

  @Test
  fun processStartedFromOstiaReportsStartedAndIsStillForwarded() {
    initiateShop()
    launch()
    appReady()
    val journeyId = deliveredJourneyId()

    reply(journeyId, JSONObject().put("eventName", "eventStream").put("event", "ProcessStarted"))

    assertEquals(listOf(BlazeLaunchResult.Started(journeyId)), results)
    assertEquals(journeyId, callbacks.last().getString("requestId"))
  }

  @Test
  fun ostiaAuthErrorsMapToTheLaunchResult() {
    for (code in listOf(BlazeLaunchError.E_AUTH_OTT_REJECTED, BlazeLaunchError.E_AUTH_UNAVAILABLE)) {
      setUp()
      scripts.clear()
      results.clear()
      initiateShop()
      launch()
      appReady()

      reply(deliveredJourneyId(), JSONObject().put("error", true).put("errorDetails", code.name))

      assertEquals(listOf(failed(code)), results)
    }
  }

  @Test
  fun aFailedJourneyFreesTheSdkForTheNextLaunch() {
    initiateShop()
    launch()
    appReady()
    reply(deliveredJourneyId(), JSONObject().put("error", true).put("errorDetails", "E_AUTH_OTT_REJECTED"))

    launch()

    assertEquals(2, scripts.count { it.contains(OTT) })
  }

  @Test
  fun launchFromASecondActivityAttachesTheWebViewThere() {
    initiateShop()
    appReady()
    val second = Robolectric.buildActivity(Activity::class.java).setup().get()

    blaze.launch(second, BlazeLaunchRequest(OTT)) { results.add(it) }
    idle()
    blazeWebView.onEvent(JSONObject().put("eventName", "renderView").toString())
    idle()

    val content = second.window.decorView.findViewById<ViewGroup>(android.R.id.content)
    assertSame(content, blazeWebView.webView.parent)
  }

  @Test
  fun theOttIsNeverLoggedOrStored() {
    initiateShop()
    launch(BlazeLaunchRequest(OTT, "bt", DEVICE_BINDING, "search", "blue shoes"))
    appReady()
    reply(deliveredJourneyId(), JSONObject().put("event", "ProcessStarted"))

    assertTrue(ShadowLog.getLogs().none { it.msg.contains(OTT) })
    for (name in listOf(BlazeConstants.SHARED_PREF_NAME, BlazeConstants.SDK_CONFIG_PREF_NAME)) {
      val stored = activity.getSharedPreferences(name, Context.MODE_PRIVATE).all.values
      assertTrue(stored.none { it.toString().contains(OTT) })
    }
  }

  private fun initiateShop() {
    blaze.initiate(activity, initiatePayload(BlazeConstants.SHOP_SERVICE)) { callbacks.add(it) }
    idle()
  }

  private fun launch(request: BlazeLaunchRequest = BlazeLaunchRequest(OTT)) {
    blaze.launch(activity, request) { results.add(it) }
    idle()
  }

  private fun appReady() {
    blazeWebView.onEvent(JSONObject().put("eventName", "appReady").toString())
    idle()
  }

  private fun reply(journeyId: String, payload: JSONObject) {
    val response = JSONObject()
      .put("requestId", journeyId)
      .put("service", BlazeConstants.SHOP_SERVICE)
      .put("payload", payload)
    val event = JSONObject().put("eventName", "callbackEvent").put("eventData", response.toString())
    blazeWebView.onEvent(event.toString())
    idle()
  }

  private fun deliveredJourneyId(): String {
    val launch = scripts.single { it.contains(OTT) }
    return eventOf(launch).getJSONObject("eventData").getString("requestId")
  }

  private fun eventOf(script: String): JSONObject {
    val json = script.substringAfter("JSON.stringify(").removeSuffix("))")
    return JSONObject(json)
  }

  private fun idle() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun failed(error: BlazeLaunchError) = BlazeLaunchResult.Failed(error)

  private fun initiatePayload(service: String): JSONObject {
    return JSONObject()
      .put("requestId", "init-1")
      .put("service", service)
      .put("payload", JSONObject().put("action", "initiate").put("publisherId", "bt-pay"))
  }

  private companion object {
    const val OTT = "OTT-0123456789-abcdef-SECRET"
    const val DEVICE_BINDING = "device-binding-42"
  }
}
