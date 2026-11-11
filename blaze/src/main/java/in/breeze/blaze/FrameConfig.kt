package `in`.breeze.blaze

import `in`.breeze.blaze.security.NavigationDecision
import `in`.breeze.blaze.security.NavigationPolicy
import org.json.JSONObject

internal data class FrameConfig(
  val defaultUrl: String,
  val allowedOrigins: Set<String>?
) {

  val isRestricted: Boolean
    get() = allowedOrigins != null

  fun resolveNavigation(url: String): NavigationDecision {
    if (allowedOrigins == null) {
      return NavigationDecision.ALLOW
    }
    return NavigationPolicy.resolveNavigation(url, allowedOrigins)
  }

  fun isFramePage(currentUrl: String?, baseUrl: String): Boolean {
    if (currentUrl == null) {
      return false
    }
    return if (allowedOrigins == null) {
      currentUrl.contains(baseUrl)
    } else {
      val origin = NavigationPolicy.originOf(currentUrl)
      origin != null && origin == NavigationPolicy.originOf(baseUrl)
    }
  }

  companion object {

    fun from(payload: JSONObject): FrameConfig {
      return FrameConfig(getBaseUrl(payload), allowedOrigins = null)
    }

    fun locked(frameUrl: String, origins: List<String>): FrameConfig? {
      val frameOrigin = NavigationPolicy.originOf(frameUrl) ?: return null
      val listed = origins.map { NavigationPolicy.bareOriginOf(it) ?: return null }
      return FrameConfig(frameUrl, setOf(frameOrigin) + listed)
    }
  }
}
