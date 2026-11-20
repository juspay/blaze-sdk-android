package `in`.breeze.blaze.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal object BrowserFlow {

  const val EXTRA_URL = "in.breeze.blaze.browser.URL"
  const val EXTRA_STATE = "in.breeze.blaze.browser.STATE"

  private class Pending(val state: String, val onResult: (JSONObject) -> Unit)

  private val pending = AtomicReference<Pending?>(null)

  fun open(activity: Activity?, url: String, onResult: (JSONObject) -> Unit) {
    if (activity == null) {
      return
    }
    val uri = url.toUri()
    if (!"https".equals(uri.scheme, ignoreCase = true) || uri.host.isNullOrEmpty()) {
      onResult(failed("invalid_url"))
      return
    }
    val state = UUID.randomUUID().toString()
    val previous = pending.getAndSet(Pending(state, onResult))
    if (previous != null) {
      previous.onResult(failed("superseded"))
    }
    val fullUrl = uri.buildUpon()
      .appendQueryParameter("state", state)
      .appendQueryParameter("redirect_uri", "${activity.packageName}.blaze://callback")
      .build()
      .toString()
    activity.runOnUiThread {
      activity.startActivity(
        Intent(activity, BrowserFlowActivity::class.java)
          .putExtra(EXTRA_URL, fullUrl)
          .putExtra(EXTRA_STATE, state)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      )
      activity.overridePendingTransition(0, 0)
    }
  }

  fun complete(flowState: String?, uri: Uri?) {
    val current = take(flowState) ?: return
    val result = when {
      uri == null -> JSONObject().put("status", "cancelled")
      uri.getQueryParameter("state") != current.state -> failed("state_mismatch")
      else -> {
        val params = JSONObject()
        uri.queryParameterNames
          .filter { it != "state" }
          .forEach { params.put(it, uri.getQueryParameter(it)) }
        JSONObject().put("status", "success").put("params", params)
      }
    }
    current.onResult(result)
  }

  fun fail(flowState: String?, error: String) {
    val current = take(flowState) ?: return
    current.onResult(failed(error))
  }

  fun clear() {
    pending.set(null)
  }

  private fun take(flowState: String?): Pending? {
    val current = pending.get() ?: return null
    if (current.state != flowState || !pending.compareAndSet(current, null)) {
      return null
    }
    return current
  }

  private fun failed(error: String): JSONObject {
    return JSONObject().put("status", "failed").put("error", error)
  }
}
