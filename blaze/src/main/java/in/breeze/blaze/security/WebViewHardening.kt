package `in`.breeze.blaze.security

import android.webkit.WebSettings
import android.webkit.WebView

internal object WebViewHardening {

  fun apply(webView: WebView) {
    with(webView.settings) {
      allowFileAccess = false
      allowContentAccess = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
  }
}
