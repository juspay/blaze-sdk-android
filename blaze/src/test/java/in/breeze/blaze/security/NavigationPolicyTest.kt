package `in`.breeze.blaze.security

import `in`.breeze.blaze.security.NavigationDecision.ALLOW
import `in`.breeze.blaze.security.NavigationDecision.BLOCK
import `in`.breeze.blaze.security.NavigationDecision.OPEN_EXTERNAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationPolicyTest {

  private val allowList = setOf("https://journey.example.com")

  private fun decide(url: String, isMainFrame: Boolean = true, drop: Boolean = false) =
    NavigationPolicy.decide(url, isMainFrame, allowList, drop)

  @Test
  fun contentOriginLoads() {
    assertEquals(ALLOW, decide("https://journey.example.com/sdk"))
    assertEquals(ALLOW, decide("https://JOURNEY.Example.com/sdk?q=1#top"))
    assertEquals(ALLOW, decide("https://journey.example.com:443/sdk"))
    assertEquals(ALLOW, decide("HTTPS://journey.example.com"))
    assertEquals(ALLOW, decide("about:blank"))
  }

  @Test
  fun otherHttpsMainFrameOpensExternally() {
    assertEquals(OPEN_EXTERNAL, decide("https://evil.example/"))
    assertEquals(OPEN_EXTERNAL, decide("https://journey.example.com.evil.example/"))
    assertEquals(OPEN_EXTERNAL, decide("https://journey.example.com@evil.example/"))
    assertEquals(OPEN_EXTERNAL, decide("https://journey.example.com:8443/sdk"))
    assertEquals(OPEN_EXTERNAL, decide("http://evil.example/"))
  }

  @Test
  fun externalLinksAreDroppedWhenFlagged() {
    assertEquals(BLOCK, decide("https://evil.example/", drop = true))
    assertEquals(ALLOW, decide("https://journey.example.com/sdk", drop = true))
  }

  @Test
  fun downgradeToHttpIsBlocked() {
    assertEquals(BLOCK, decide("http://journey.example.com/sdk"))
    assertEquals(BLOCK, decide("http://JOURNEY.example.com:80/sdk"))
  }

  @Test
  fun otherSchemesAreBlocked() {
    listOf(
      "file:///etc/hosts",
      "content://x",
      "javascript:alert(1)",
      "data:text/html,x",
      "blob:https://journey.example.com/uuid",
      "intent://x#Intent;scheme=https;end",
      "about:srcdoc",
      "upi://pay?pa=a&pn=b&am=1",
      "ftp://journey.example.com/",
      "not a url",
      ""
    ).forEach { assertEquals(it, BLOCK, decide(it)) }
  }

  @Test
  fun subFramesOutsideTheAllowListAreBlocked() {
    assertEquals(BLOCK, decide("https://evil.example/", isMainFrame = false))
    assertEquals(ALLOW, decide("https://journey.example.com/frame", isMainFrame = false))
  }

  @Test
  fun emptyAllowListFailsClosed() {
    assertEquals(BLOCK, NavigationPolicy.decide("https://journey.example.com/sdk", true, emptySet()))
    assertEquals(BLOCK, NavigationPolicy.decide("https://evil.example/", true, emptySet()))
  }

  @Test
  fun originOfNormalisesHttpsOnly() {
    assertEquals("https://journey.example.com", NavigationPolicy.originOf("https://Journey.Example.com:443/sdk"))
    assertEquals("https://journey.example.com:8443", NavigationPolicy.originOf("https://journey.example.com:8443/"))
    assertNull(NavigationPolicy.originOf("http://journey.example.com"))
    assertNull(NavigationPolicy.originOf("file:///etc/hosts"))
    assertNull(NavigationPolicy.originOf("not a url"))
  }
}
