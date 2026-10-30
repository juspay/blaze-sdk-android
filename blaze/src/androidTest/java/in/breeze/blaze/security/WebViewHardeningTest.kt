package `in`.breeze.blaze.security

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewHardeningTest {

  @Test
  @Suppress("DEPRECATION")
  fun settingsAreLockedDown() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
      val webView = WebView(instrumentation.targetContext)
      WebViewHardening.apply(webView)

      with(webView.settings) {
        assertFalse(allowFileAccess)
        assertFalse(allowContentAccess)
        assertFalse(allowFileAccessFromFileURLs)
        assertFalse(allowUniversalAccessFromFileURLs)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, mixedContentMode)
        assertFalse(javaScriptCanOpenWindowsAutomatically)
        assertFalse(supportMultipleWindows())
        assertTrue(mediaPlaybackRequiresUserGesture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          assertFalse(safeBrowsingEnabled)
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        assertTrue(webView.webChromeClient != null)
      }
      webView.destroy()
    }
  }
}
