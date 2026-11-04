package `in`.breeze.blaze

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import `in`.breeze.blaze.security.WebViewHardening
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import androidx.core.content.edit
import kotlin.math.roundToInt


internal class BlazeWebView @SuppressLint(
  "SetJavaScriptEnabled",
  "JavascriptInterface"
) constructor(
  context: Activity,
  private val initiatePayload: JSONObject,
  container: FrameLayout?,
  private val callbackFn: CallbackFn
) {

  private val contextRef: WeakReference<Activity> = WeakReference(context)
  private var containerRef: WeakReference<ViewGroup>? = container?.let { WeakReference(it) }
  private val webView: WebView = WebView(context)
  @Volatile
  private var isWebViewReady: Boolean = false
  @Volatile
  private var isTerminated: Boolean = false
  private var lastSafeMargin: SafeMargin? = null
  private val safeMarginLayoutListener =
    View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> sendSafeMarginIfChanged() }
  @Volatile
  private var consumingBackPress: Boolean = false
  private var eventQueue: HashMap<String, JSONObject> = hashMapOf()
  private val sharedPreferences: SharedPreferences
  @Volatile
  private var frame: FrameMode = FrameMode.Unlocked(getBaseUrl(initiatePayload))
  private val frameLoadTarget: AtomicReference<BlazeWebView?> = AtomicReference(this)

  private val blazeWebViewClient = object : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return handleNavigation(request?.url?.toString(), request?.isForMainFrame ?: true)
    }

    @Deprecated("Required for API < 24 compatibility")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return handleNavigation(url)
    }

    override fun shouldInterceptRequest(
      view: WebView?,
      request: WebResourceRequest?
    ): WebResourceResponse? {
      if (request == null || !request.isForMainFrame) {
        return null
      }
      if (frame.allows(request.url.toString())) {
        return null
      }
      Log.w("BlazeSDK: navigation: ", "Blocked ${request.method} to ${originForLog(request.url.toString())}")
      returnToAllowedPage()
      return WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Forbidden",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
      )
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
      super.onPageStarted(view, url, favicon)
      if (url != null && !frame.allows(url)) {
        Log.w("BlazeSDK: navigation: ", "Blocked page load on ${originForLog(url)}")
        returnToAllowedPage()
      }
    }
  }

  init {
    this.sharedPreferences =
      context.getSharedPreferences(BlazeConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE)
    this.webView.settings.javaScriptEnabled = true
    this.webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
    this.webView.settings.domStorageEnabled = true
    this.webView.setBackgroundColor(Color.TRANSPARENT)
    WebViewHardening.apply(this.webView)
    this.webView.webViewClient = blazeWebViewClient
    this.webView.addJavascriptInterface(this, "Native")
    ViewCompat.setOnApplyWindowInsetsListener(this.webView) { view, insets ->
      view.post { sendSafeMarginIfChanged() }
      ViewCompat.onApplyWindowInsets(view, insets)
    }
    this.webView.addOnLayoutChangeListener(safeMarginLayoutListener)
    this.sendEvent("initiate", JSONObject(this.initiatePayload.toString()))
    this.loadFrame(context)
  }

  private fun loadFrame(context: Activity) {
    val target = frameLoadTarget
    BlazeConfig.resolveFrameUrl(
      context,
      getService(initiatePayload),
      getEnvironment(initiatePayload)
    ) { remote -> target.getAndSet(null)?.renderFrame(remote) }
  }

  private fun renderFrame(remote: RemoteFrame?) {
    val resolved = FrameMode.resolve(remote, getBaseUrl(initiatePayload))
    frame = resolved
    if (resolved is FrameMode.Failed) {
      reportError("initiate", initiatePayload, resolved.failure)
      contextRef.get()?.runOnUiThread {
        eventQueue.remove("process")?.let { reportError("process", it, resolved.failure) }
      }
      return
    }
    val url = resolved.url ?: return
    val activity = contextRef.get() ?: return
    if (activity.isFinishing || activity.isDestroyed) {
      return
    }
    activity.runOnUiThread { webView.loadUrl(url) }
  }


  fun process(payload: JSONObject) {
    contextRef.get()?.runOnUiThread {
      val current = frame
      if (current is FrameMode.Failed) {
        reportError("process", payload, current.failure)
      } else {
        this.sendEvent("process", payload)
      }
    }
  }

  fun handleBackPress(): Boolean {
    if (consumingBackPress) {
      contextRef.get()?.runOnUiThread {
        if (frame.isFramePage(webView.url)) {
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
    isTerminated = true
    frameLoadTarget.set(null)
    this.sendEvent("terminate", JSONObject())
    hideView()
    contextRef.get()?.runOnUiThread {
      getContainer()?.removeOnLayoutChangeListener(safeMarginLayoutListener)
      webView.stopLoading()
      webView.removeJavascriptInterface("Native")
      this.webView.destroy()
      eventQueue.clear()
    }
  }


  @JavascriptInterface
  fun onEvent(event: String) {
    val eventJson = safeParseJson(event)
    val eventName = eventJson.getString("eventName")
    val eventData = eventJson.optString("eventData")
    val eventDataJson = safeParseJson(eventData)

    if (eventName == "appReady") {
      contextRef.get()?.runOnUiThread { sendInitiate() }
    }
    if (eventName == "callbackEvent") {
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
      hideView()
    }

    if (eventName == "invokeMethod") {
      handleInvokeMethod(eventDataJson)
    }

  }


  private fun sendEvent(event: String, payload: JSONObject) {
    val eventMessage =
      JSONObject()
        .put("eventName", event)
        .put("eventData", payload)
        .put("source", "blaze")
    contextRef.get()?.runOnUiThread {
      if (isWebViewReady) {
        webView.evaluateJavascript("javascript:onSDKEvent(JSON.stringify($eventMessage))") {}
      } else {
        eventQueue[event] = payload
      }
    }
  }

  private fun handleNavigation(url: String?, isMainFrame: Boolean = true): Boolean {
    if (url == null) {
      return frame !is FrameMode.Unlocked
    }
    if (isUPIIntentUri(url.toUri())) {
      openApp(url)
      return true
    }
    if (!isMainFrame || frame.allows(url)) {
      return false
    }
    Log.w("BlazeSDK: navigation: ", "Blocked link to ${originForLog(url)}")
    return true
  }

  private fun originForLog(url: String): String {
    val uri = url.toUri()
    return "${uri.scheme}://${uri.host}"
  }

  private fun returnToAllowedPage() {
    contextRef.get()?.runOnUiThread {
      if (isTerminated) {
        return@runOnUiThread
      }
      webView.stopLoading()
      val currentUrl = webView.url
      if (currentUrl == null || frame.allows(currentUrl)) {
        return@runOnUiThread
      }
      frame.url?.let { webView.loadUrl(it) }
    }
  }

  private fun canOpenApp(intentUri: String): Boolean {
    return frame is FrameMode.Unlocked || isUPIIntentUri(intentUri.toUri())
  }

  private fun reportError(eventName: String, request: JSONObject, failure: FrameFailure) {
    callbackFn.invoke(sdkErrorResponse(request, eventName, failure.code, failure.details))
  }

  private fun drainEventQueue() {
    val pendingEvents = eventQueue.toList()
    eventQueue.clear()
    pendingEvents.forEach { (event, payload) ->
      sendEvent(event, payload)
    }
  }

  private fun sendInitiate() {
    if (isTerminated) {
      return
    }
    val safeMargin = getSafeMargin()
    lastSafeMargin = safeMargin
    eventQueue["initiate"]?.optJSONObject("payload")?.put("_sdkParams", SdkParams(safeMargin).toJson())
    isWebViewReady = true
    drainEventQueue()
    getContainer()?.run {
      removeOnLayoutChangeListener(safeMarginLayoutListener)
      addOnLayoutChangeListener(safeMarginLayoutListener)
    }
  }

  private fun sendSafeMarginIfChanged() {
    if (isTerminated || lastSafeMargin == null) {
      return
    }
    val safeMargin = getSafeMargin()
    if (safeMargin != lastSafeMargin) {
      lastSafeMargin = safeMargin
      sendEvent("_sdkParams", SdkParams(safeMargin).toJson())
    }
  }

  private fun getContainer(): ViewGroup? {
    containerRef?.let { return it.get() }
    val container: ViewGroup =
      contextRef.get()?.findViewById(android.R.id.content) ?: return null
    containerRef = WeakReference(container)
    return container
  }

  private fun renderView() {
    contextRef.get()?.runOnUiThread {
      this.webView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      if (webView.parent == null) {
        getContainer()?.addView(webView)
        ViewCompat.requestApplyInsets(webView)
      }
    }
  }

  private fun getSafeMargin(): SafeMargin {
    val activity = contextRef.get() ?: return SafeMargin()
    val container = getContainer() ?: return SafeMargin()
    val view = if (webView.isAttachedToWindow && webView.isLaidOut) webView else container
    if (!view.isLaidOut) return SafeMargin()
    val insets = ViewCompat.getRootWindowInsets(view)
      ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      ?: return SafeMargin()
    val root = view.rootView
    val location = IntArray(2)
    view.getLocationInWindow(location)
    val left = location[0] + view.paddingLeft
    val top = location[1] + view.paddingTop
    val right = location[0] + view.width - view.paddingRight
    val bottom = location[1] + view.height - view.paddingBottom
    val density = activity.resources.displayMetrics.density
    return SafeMargin(
      top = toDp(insets.top - top, density),
      right = toDp(right - (root.width - insets.right), density),
      bottom = toDp(bottom - (root.height - insets.bottom), density),
      left = toDp(insets.left - left, density)
    )
  }

  private fun toDp(px: Int, density: Float): Int {
    return (maxOf(px, 0) / density).roundToInt()
  }

  private fun hideView() {
    contextRef.get()?.runOnUiThread {
      this.webView.layoutParams = FrameLayout.LayoutParams(0, 0)
      getContainer()?.removeView(webView)
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
      if (canOpenApp(intentUri)) {
        invokeResult.put("methodResult", openApp(intentUri))
      } else {
        Log.w("BlazeSDK: navigation: ", "Blocked openApp for ${originForLog(intentUri)}")
        invokeResult.put("methodResult", "Blocked intent: only UPI payment links can be opened")
      }
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