package `in`.breeze.blaze.security

import java.net.URI
import java.util.Locale

internal enum class NavigationDecision {
  ALLOW,
  BLOCK
}

internal object NavigationPolicy {

  private const val HTTPS = "https"
  private const val DEFAULT_HTTPS_PORT = 443
  private const val ABOUT_BLANK = "about:blank"

  fun resolveNavigation(
    url: String,
    allowList: Set<String>
  ): NavigationDecision {
    if (allowList.isEmpty()) {
      return NavigationDecision.BLOCK
    }
    if (url.equals(ABOUT_BLANK, ignoreCase = true)) {
      return NavigationDecision.ALLOW
    }
    return if (originOf(url) in allowList) {
      NavigationDecision.ALLOW
    } else {
      NavigationDecision.BLOCK
    }
  }

  fun originOf(url: String): String? {
    val uri = parse(url) ?: return null
    if (!HTTPS.equals(uri.scheme, ignoreCase = true)) {
      return null
    }
    return originOf(uri)
  }

  fun bareOriginOf(value: String): String? {
    val trimmed = value.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd == -1) {
      return null
    }
    if (trimmed.indexOfAny(charArrayOf('/', '?', '#', '@', '\\'), schemeEnd + 3) != -1) {
      return null
    }
    return originOf(trimmed)
  }

  private fun originOf(uri: URI): String? {
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val port = if (uri.port == -1 || uri.port == DEFAULT_HTTPS_PORT) {
      ""
    } else {
      ":${uri.port}"
    }
    return "$HTTPS://$host$port"
  }

  private fun parse(url: String): URI? {
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf("://")
    val head = if (schemeEnd == -1) {
      trimmed
    } else {
      val authorityEnd = trimmed.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd + 3)
      if (authorityEnd == -1) {
        trimmed
      } else {
        trimmed.substring(0, authorityEnd)
      }
    }
    return try {
      URI(head)
    } catch (e: Exception) {
      null
    }
  }
}
