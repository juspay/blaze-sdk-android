package `in`.breeze.blaze.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle

internal class BrowserRedirectActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startActivity(
      Intent(this, BrowserFlowActivity::class.java)
        .setData(intent.data)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
    finish()
    overridePendingTransition(0, 0)
  }
}
