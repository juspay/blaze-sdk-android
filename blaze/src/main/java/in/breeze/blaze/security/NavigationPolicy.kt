package `in`.breeze.blaze.security

import java.net.URI
import java.util.Locale

internal enum class NavigationDecision {
  ALLOW,
  OPEN_EXTERNAL,
  BLOCK
}

internal object NavigationPolicy {

  private const val HTTPS = "https"
  private const val HTTP = "http"
  private const val DEFAULT_HTTPS_PORT = 443
  private const val ABOUT_BLANK = "about:blank"

  fun decide(
    url: String,
    isMainFrame: Boolean,
    allowList: Set<String>,
    dropExternalLinks: Boolean = false
  ): NavigationDecision {
    if (allowList.isEmpty()) {
      return NavigationDecision.BLOCK
    }
    if (url.equals(ABOUT_BLANK, ignoreCase = true)) {
      return NavigationDecision.ALLOW
    }

    val uri = parse(url) ?: return NavigationDecision.BLOCK
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return NavigationDecision.BLOCK
    if (scheme != HTTPS && scheme != HTTP) {
      return NavigationDecision.BLOCK
    }
    val host = uri.host?.lowercase(Locale.ROOT) ?: return NavigationDecision.BLOCK

    if (scheme == HTTPS && originOf(uri) in allowList) {
      return NavigationDecision.ALLOW
    }
    if (scheme == HTTP && allowList.any { hostOf(it) == host }) {
      return NavigationDecision.BLOCK
    }
    if (!isMainFrame || dropExternalLinks) {
      return NavigationDecision.BLOCK
    }
    return NavigationDecision.OPEN_EXTERNAL
  }

  fun originOf(url: String): String? {
    val uri = parse(url) ?: return null
    if (!HTTPS.equals(uri.scheme, ignoreCase = true)) {
      return null
    }
    return originOf(uri)
  }

  private fun originOf(uri: URI): String? {
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val port = if (uri.port == -1 || uri.port == DEFAULT_HTTPS_PORT) "" else ":${uri.port}"
    return "$HTTPS://$host$port"
  }

  private fun hostOf(origin: String): String? = parse(origin)?.host?.lowercase(Locale.ROOT)

  private fun parse(url: String): URI? {
    return try {
      URI(url.trim())
    } catch (e: Exception) {
      null
    }
  }
}
