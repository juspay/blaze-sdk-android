package `in`.breeze.blaze

import android.app.Activity
import android.content.Intent
import androidx.annotation.Keep
import org.json.JSONObject
import java.lang.ref.WeakReference


typealias CallbackFn = (event: JSONObject) -> Unit;

class Blaze {

  private var webView: BlazeWebView? = null;
  private var contextRef: WeakReference<Activity>? = null;
  private var callbackFn: CallbackFn? = null;
  private var isInitialized: Boolean = false;

  fun initiate(context: Activity, initiatePayload: JSONObject, callbackFn: CallbackFn) {
    this.contextRef = WeakReference(context)
    this.callbackFn = callbackFn;
    if (!isInitialized) {
      context.runOnUiThread {
        this.webView = BlazeWebView(context, initiatePayload, callbackFn)
      }
      this.isInitialized = true
    }
  }

  fun process(payload: JSONObject) {
    this.webView?.process(payload)
  }

  fun handleBackPress(): Boolean {
    return this.webView?.handleBackPress() ?: true

  }

  fun terminate() {
    this.webView?.terminate()
    webView = null
    callbackFn = null
    contextRef?.clear()
    contextRef = null
    isInitialized = false
  }

  @Keep
  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
  }

  @Keep
  fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
  }

}