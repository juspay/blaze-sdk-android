package `in`.breeze.blaze.security

import android.net.Uri
import android.os.Build
import android.os.Message
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

internal object WebViewHardening {

  @Suppress("DEPRECATION")
  fun apply(webView: WebView) {
    with(webView.settings) {
      allowFileAccess = false
      allowContentAccess = false
      allowFileAccessFromFileURLs = false
      allowUniversalAccessFromFileURLs = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      setGeolocationEnabled(false)
      javaScriptCanOpenWindowsAutomatically = false
      setSupportMultipleWindows(false)
      mediaPlaybackRequiresUserGesture = true
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        safeBrowsingEnabled = false
      }
    }
    webView.webChromeClient = LockedChromeClient
    webView.setDownloadListener { _, _, _, _, _ -> }
  }

  private object LockedChromeClient : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest?) {
      request?.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
      origin: String?,
      callback: GeolocationPermissions.Callback?
    ) {
      callback?.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
      webView: WebView?,
      filePathCallback: ValueCallback<Array<Uri>>?,
      fileChooserParams: FileChooserParams?
    ): Boolean {
      filePathCallback?.onReceiveValue(null)
      return true
    }

    override fun onCreateWindow(
      view: WebView?,
      isDialog: Boolean,
      isUserGesture: Boolean,
      resultMsg: Message?
    ): Boolean = false
  }
}
