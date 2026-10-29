package `in`.breeze.blaze

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import androidx.core.content.edit

internal typealias FrameUrlResolver =
  (context: Activity, service: String, environment: String, callback: (frameUrl: String?) -> Unit) -> Unit
internal typealias ScriptRunner = (webView: WebView, script: String) -> Unit

internal class BlazeWebView @SuppressLint(
  "SetJavaScriptEnabled",
  "JavascriptInterface"
) constructor(
  context: Activity,
  private val initiatePayload: JSONObject,
  private val callbackFn: CallbackFn,
  private val resolveFrameUrl: FrameUrlResolver = { activity, service, environment, callback ->
    BlazeConfig.resolveFrameUrl(activity, service, environment, callback)
  },
  private val runScript: ScriptRunner = { view, script -> view.evaluateJavascript(script) {} }
) {

  private val contextWrapper: MutableContextWrapper = MutableContextWrapper(context)
  @Volatile
  private var contextRef: WeakReference<Activity> = WeakReference(context)
  internal val webView: WebView = WebView(contextWrapper)
  internal val service: String = getService(initiatePayload)
  private var consumingBackPress: Boolean = false
  private val sharedPreferences: SharedPreferences
  private var baseUrl: String = getBaseUrl(initiatePayload)
  private val frameLoadTarget: AtomicReference<BlazeWebView?> = AtomicReference(this)

  private val lock = Any()
  private val mainHandler = Handler(Looper.getMainLooper())
  private var isWebViewReady: Boolean = false
  private val eventQueue: ArrayDeque<Pair<String, JSONObject>> = ArrayDeque()
  private var journey: Journey? = null

  @Volatile
  private var contentOrigin: String? = null

  private class Journey(
    val id: String,
    val onResult: (BlazeLaunchResult) -> Unit,
    private var launchEvent: JSONObject?
  ) {
    var delivered: Boolean = false
    var settled: Boolean = false

    fun takeLaunchEvent(): JSONObject? = launchEvent.also { launchEvent = null }
  }

  private val blazeWebViewClient = object : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url
        if (url != null && isUPIIntentUri(url)) {
            openApp(url.toString())
            return true
        }
        return false
    }

    @Deprecated("Required for API < 24 compatibility")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url != null && isUPIIntentUri(url.toUri())) {
            openApp(url)
            return true
        }
        return false
    }
  }

  init {
    this.sharedPreferences =
      context.getSharedPreferences(BlazeConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE)
    this.webView.settings.javaScriptEnabled = true
    this.webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
    this.webView.settings.domStorageEnabled = true
    this.webView.webViewClient = blazeWebViewClient
    this.webView.addJavascriptInterface(this, "Native")
    this.sendEvent("initiate", this.initiatePayload)
    this.loadFrame(context)
  }

  private fun loadFrame(context: Activity) {
    val target = frameLoadTarget
    resolveFrameUrl(
      context,
      service,
      getEnvironment(initiatePayload)
    ) { frameUrl -> target.getAndSet(null)?.renderFrame(frameUrl) }
  }

  private fun renderFrame(frameUrl: String?) {
    if (frameUrl != null) {
      baseUrl = frameUrl
    }
    val activity = contextRef.get() ?: return
    if (activity.isFinishing || activity.isDestroyed) {
      return
    }
    activity.runOnUiThread {
      contentOrigin = originOf(baseUrl)
      webView.loadUrl(baseUrl)
    }
  }


  fun process(payload: JSONObject) {
    this.sendEvent("process", payload)
  }

  fun launch(activity: Activity, request: BlazeLaunchRequest, onResult: (BlazeLaunchResult) -> Unit) {
    val journeyId = UUID.randomUUID().toString()
    val accepted = synchronized(lock) {
      if (journey != null) {
        false
      } else {
        journey = Journey(journeyId, onResult, launchEventFor(journeyId, request))
        true
      }
    }
    if (!accepted) {
      mainHandler.post { onResult(BlazeLaunchResult.Failed(BlazeLaunchError.E_JOURNEY_IN_PROGRESS)) }
      return
    }
    mainHandler.post { rebase(activity) }
    deliverLaunch()
  }

  fun handleBackPress(): Boolean {
    if (consumingBackPress) {
      contextRef.get()?.runOnUiThread {
        val currentPageUrl = webView.url
        val isBreezePage = currentPageUrl?.contains(baseUrl) ?: false
        if (isBreezePage) {
          this.sendEvent("backPress", JSONObject())
        } else if (webView.canGoBack()) {
          webView.goBack()
        }
      }
      return false
    }
    return true
  }

  fun terminate() {
    frameLoadTarget.set(null)
    val unsettled = synchronized(lock) { journey?.takeIf { !it.settled } }
    unsettled?.let { settle(it, BlazeLaunchResult.Failed(BlazeLaunchError.E_CONTENT_LOAD_FAILED)) }
    this.sendEvent("terminate", JSONObject())
    hideView()
    mainHandler.post {
      webView.stopLoading()
      webView.removeJavascriptInterface("Native")
      this.webView.destroy()
    }
    synchronized(lock) {
      eventQueue.clear()
      journey = null
    }
  }


  @JavascriptInterface
  fun onEvent(event: String) {
    val eventJson = safeParseJson(event)
    val eventName = eventJson.getString("eventName")
    val eventData = eventJson.optString("eventData")
    val eventDataJson = safeParseJson(eventData)

    if (eventName == "appReady") {
      onAppReady()
    }
    if (eventName == "callbackEvent") {
      settleFromResponse(eventDataJson)
      this.callbackFn.invoke(eventDataJson)
    }

    if (eventName == "consumeBackPress") {
      consumingBackPress = true
    }

    if (eventName == "releaseBackPress") {
      consumingBackPress = false
    }

    if (eventName == "renderView") {
      renderView()
    }

    if (eventName == "hideView") {
      endSettledJourney()
      hideView()
    }

    if (eventName == "invokeMethod") {
      handleInvokeMethod(eventDataJson)
    }

  }


  private fun sendEvent(event: String, payload: JSONObject) {
    synchronized(lock) {
      if (isWebViewReady) {
        postScript(event, payload)
      } else {
        eventQueue.addLast(event to payload)
      }
    }
  }

  private fun scriptFor(event: String, payload: JSONObject): String {
    val eventMessage =
      JSONObject()
        .put("eventName", event)
        .put("eventData", payload)
        .put("source", "blaze")
    return "javascript:onSDKEvent(JSON.stringify($eventMessage))"
  }

  private fun postScript(event: String, payload: JSONObject) {
    val script = scriptFor(event, payload)
    mainHandler.post { runScript(webView, script) }
  }

  private fun onAppReady() {
    val interrupted = synchronized(lock) {
      val lost = journey?.takeIf { it.delivered && !it.settled }
      isWebViewReady = true
      while (eventQueue.isNotEmpty()) {
        val (event, payload) = eventQueue.removeFirst()
        postScript(event, payload)
      }
      lost
    }
    interrupted?.let { settle(it, BlazeLaunchResult.Failed(BlazeLaunchError.E_CONTENT_LOAD_FAILED)) }
    deliverLaunch()
  }

  private fun deliverLaunch() {
    synchronized(lock) {
      val current = journey ?: return
      if (!isWebViewReady || current.delivered) {
        return
      }
      current.delivered = true
      mainHandler.post { deliver(current) }
    }
  }

  private fun deliver(current: Journey) {
    val launchEvent = synchronized(lock) {
      if (journey === current) current.takeLaunchEvent() else null
    } ?: return
    val loadedOrigin = originOf(webView.url)
    if (loadedOrigin == null || loadedOrigin != contentOrigin) {
      settle(current, BlazeLaunchResult.Failed(BlazeLaunchError.E_CONTENT_LOAD_FAILED))
      return
    }
    runScript(webView, scriptFor("process", launchEvent))
  }

  private fun settleFromResponse(response: JSONObject) {
    val current = synchronized(lock) { journey } ?: return
    if (response.optString("requestId") != current.id) {
      return
    }
    val payload = response.optJSONObject("payload") ?: return
    if (payload.optBoolean("error")) {
      val error = BlazeLaunchError.fromContentError(payload.optString("errorDetails"))
      settle(current, BlazeLaunchResult.Failed(error))
    } else if (payload.optString("event") == "ProcessStarted") {
      settle(current, BlazeLaunchResult.Started(current.id))
    }
  }

  private fun settle(target: Journey, result: BlazeLaunchResult) {
    synchronized(lock) {
      if (target.settled) {
        return
      }
      target.settled = true
      target.takeLaunchEvent()
      if (result is BlazeLaunchResult.Failed && journey === target) {
        journey = null
      }
    }
    mainHandler.post { target.onResult(result) }
  }

  private fun endSettledJourney() {
    synchronized(lock) {
      if (journey?.settled == true) {
        journey = null
      }
    }
  }

  private fun rebase(activity: Activity) {
    contextWrapper.baseContext = activity
    contextRef = WeakReference(activity)
    (webView.parent as? ViewGroup)?.removeView(webView)
  }

  private fun launchEventFor(journeyId: String, request: BlazeLaunchRequest): JSONObject {
    val payload = JSONObject()
      .put("action", BlazeConstants.LAUNCH_ACTION)
      .put("ottToken", request.oneTimeToken)
      .put("tenant", request.tenant ?: JSONObject.NULL)
      .put("deviceBinding", request.deviceBinding ?: JSONObject.NULL)
      .put("entryPoint", request.entryPoint ?: JSONObject.NULL)
      .put("searchText", request.searchText ?: JSONObject.NULL)
    return JSONObject()
      .put("requestId", journeyId)
      .put("service", BlazeConstants.SHOP_SERVICE)
      .put("payload", payload)
  }

  private fun renderView() {
    contextRef.get()?.runOnUiThread {
      this.webView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      val rootView =
        contextRef.get()?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
      rootView?.addView(webView)
    }
  }

  private fun hideView() {
    contextRef.get()?.runOnUiThread {
      this.webView.layoutParams = FrameLayout.LayoutParams(0, 0)
      val rootView =
        contextRef.get()?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
      rootView?.removeView(webView)
    }
  }

  private fun handleInvokeMethod(eventData: JSONObject) {
    val methodName = eventData.optString("methodName")
    val requestId = eventData.optString("requestId")
    val params = eventData.optString("params")
    val paramsJson = safeParseJson(params)

    val invokeResult = JSONObject().put("requestId", requestId).put("methodName", methodName)

    if (methodName == "saveToStorage") {
      val key = paramsJson.optString("key")
      val value = paramsJson.optString("value")
      invokeResult.put("methodResult", saveToStorage(key, value))
    } else if (methodName == "openApp") {
      val intentUri = paramsJson.optString("intentUri")
      invokeResult.put("methodResult", openApp(intentUri))
    } else if (methodName == "getFromStorage") {
      val key = paramsJson.optString("key")
      val value = getFromStorage(key)
      invokeResult.put("methodResult", JSONObject().put("key", key).put("value", value))
    } else if (methodName == "findApps") {
      val payload = paramsJson.optString("payload")
      invokeResult.put("methodResult", findApps(payload))
    } else {
      invokeResult.put("methodResult", "Method not found")
    }
    sendEvent("invokeMethodResult", invokeResult)
  }

  private fun saveToStorage(key: String, value: String): Boolean {
    try {
        sharedPreferences.edit() {
            putString(key, value)
        }
      return true
    } catch (e: Exception) {
      Log.e("BlazeSDK: Failure: ", e.message.toString())
      return false
    }
  }

  private fun getFromStorage(key: String): String? {
    try {
      return sharedPreferences.getString(key, null)
    } catch (e: Exception) {
      Log.e("BlazeSDK: Failure: ", e.message.toString())
      return null
    }
  }

  private fun openApp(
    intentUri: String
  ): String {
    try {
      val intent = Intent(Intent.ACTION_VIEW, intentUri.toUri())
      contextRef.get()?.startActivityForResult(intent, 0)
      return "Successfully triggered intent"
    } catch (e: Exception) {
      return "Failed to invoke intent: " + e.message.toString()
    }
  }

  private fun findApps(payload: String): String {
    val pm = contextRef.get()?.packageManager
    val apps = JSONArray()

    if (pm !== null) {
      val upiApps = Intent()
      upiApps.setData(payload.toUri())
      val launchables: List<ResolveInfo> = pm.queryIntentActivities(upiApps, 0)
      Collections.sort(launchables, ResolveInfo.DisplayNameComparator(pm))
      for (resolveInfo in launchables) {
        val jsonObject = JSONObject()
        try {
          val appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0)
          jsonObject.put("packageName", appInfo.packageName)
          jsonObject.put("appName", pm.getApplicationLabel(appInfo))
          apps.put(jsonObject)
        } catch (e: Exception) {
          Log.e("BlazeSDK: findApps: ", e.message.toString())
        }
      }
    }
    return apps.toString()
  }

}