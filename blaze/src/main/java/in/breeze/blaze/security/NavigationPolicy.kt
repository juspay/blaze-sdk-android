package `in`.breeze.blaze.security

import java.net.URI
import java.util.Locale

internal object NavigationPolicy {

  private const val HTTPS = "https"
  private const val DEFAULT_HTTPS_PORT = 443
  private const val ABOUT_BLANK = "about:blank"

  fun isAllowed(url: String, allowList: Set<String>): Boolean {
    return url.equals(ABOUT_BLANK, ignoreCase = true) || originOf(url) in allowList
  }

  fun originOf(url: String): String? {
    val uri = parse(url) ?: return null
    if (!HTTPS.equals(uri.scheme, ignoreCase = true)) {
      return null
    }
    return originOf(uri)
  }

  fun parseAllowedOrigin(value: String): String? {
    val trimmed = value.trim().removeSuffix("/")
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
