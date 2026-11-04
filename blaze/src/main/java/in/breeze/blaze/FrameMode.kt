package `in`.breeze.blaze

import android.util.Log
import `in`.breeze.blaze.security.NavigationPolicy

internal class FrameFailure(val code: BlazeErrorCodes, val details: String)

internal sealed class FrameMode {

  abstract val url: String?

  class Unlocked(override val url: String) : FrameMode()

  class Locked(override val url: String, val origins: Set<String>) : FrameMode()

  class Failed(val failure: FrameFailure) : FrameMode() {
    override val url: String? = null
  }

  fun allows(target: String): Boolean = when (this) {
    is Unlocked -> true
    is Locked -> NavigationPolicy.isAllowed(target, origins)
    is Failed -> false
  }

  fun isFramePage(currentUrl: String?): Boolean = when {
    currentUrl == null -> false
    this is Unlocked -> currentUrl.contains(url)
    this is Locked -> NavigationPolicy.originOf(currentUrl).let { it != null && it == NavigationPolicy.originOf(url) }
    else -> false
  }

  companion object {

    fun resolve(remote: RemoteFrame?, defaultUrl: String): FrameMode {
      if (remote?.allowedOrigins == null) {
        return Unlocked(remote?.frameUrl ?: defaultUrl)
      }
      val frameUrl = remote.frameUrl
      val frameOrigin = frameUrl?.let { NavigationPolicy.originOf(it) }
      if (frameUrl == null || frameOrigin == null) {
        Log.e("BlazeSDK: config: ", "sdk.json has no https frame URL for this locked service and environment")
        return Failed(FrameFailure(BlazeErrorCodes.FRAME_URL_UNAVAILABLE, "The assistant can't load right now. Try again later."))
      }
      val listed = remote.allowedOrigins.mapNotNull { entry ->
        val origin = NavigationPolicy.parseAllowedOrigin(entry)
        if (origin == null) {
          Log.w("BlazeSDK: config: ", "Ignored allowedOrigins entry that isn't an https origin: $entry")
        }
        origin
      }
      return Locked(frameUrl, setOf(frameOrigin) + listed)
    }
  }
}
