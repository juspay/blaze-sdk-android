package `in`.breeze.blaze.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.net.toUri

internal class BrowserFlowActivity : Activity() {

  private var tabOpened = false
  private var done = false
  private var flowState: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tabOpened = savedInstanceState?.getBoolean(KEY_TAB_OPENED) ?: false
    flowState = intent.getStringExtra(BrowserFlow.EXTRA_STATE)
  }

  override fun onResume() {
    super.onResume()
    if (done) {
      return
    }
    if (tabOpened) {
      finishWith { BrowserFlow.complete(flowState, null) }
      return
    }
    val url = intent.getStringExtra(BrowserFlow.EXTRA_URL)
    if (url == null) {
      finishWith { BrowserFlow.fail(flowState, "invalid_url") }
      return
    }
    tabOpened = true
    if (!launchTab(url, pickBrowser()) && !launchTab(url, null)) {
      finishWith { BrowserFlow.fail(flowState, "no_browser") }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.hasExtra(BrowserFlow.EXTRA_URL)) {
      setIntent(intent)
      flowState = intent.getStringExtra(BrowserFlow.EXTRA_STATE)
      tabOpened = false
      return
    }
    finishWith { BrowserFlow.complete(flowState, intent.data) }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(KEY_TAB_OPENED, tabOpened)
  }

  private fun launchTab(url: String, browserPackage: String?): Boolean {
    val extras = Bundle()
    extras.putBinder(EXTRA_CUSTOM_TABS_SESSION, null)
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).putExtras(extras)
    if (browserPackage != null) {
      intent.setPackage(browserPackage)
    }
    return try {
      startActivity(intent)
      true
    } catch (e: ActivityNotFoundException) {
      false
    }
  }

  private fun pickBrowser(): String? {
    val customTabBrowsers = packageManager
      .queryIntentServices(Intent(ACTION_CUSTOM_TABS_CONNECTION), 0)
      .map { it.serviceInfo.packageName }
      .toSet()
    val defaultBrowser = packageManager
      .resolveActivity(Intent(Intent.ACTION_VIEW, "https://".toUri()), PackageManager.MATCH_DEFAULT_ONLY)
      ?.activityInfo
      ?.packageName
    if (defaultBrowser in PASSKEY_BROWSERS && defaultBrowser in customTabBrowsers) {
      return defaultBrowser
    }
    return PASSKEY_BROWSERS.firstOrNull { it in customTabBrowsers }
  }

  private fun finishWith(block: () -> Unit) {
    done = true
    block()
    finish()
    overridePendingTransition(0, 0)
  }

  private companion object {
    const val KEY_TAB_OPENED = "tabOpened"
    const val EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"
    const val ACTION_CUSTOM_TABS_CONNECTION = "android.support.customtabs.action.CustomTabsService"
    val PASSKEY_BROWSERS = listOf(
      "com.android.chrome",
      "com.sec.android.app.sbrowser",
      "com.microsoft.emmx",
      "com.brave.browser"
    )
  }
}
