package `in`.breeze.blaze

class BlazeLaunchRequest @JvmOverloads constructor(
  val oneTimeToken: String,
  val tenant: String? = null,
  val deviceBinding: String? = null,
  val entryPoint: String? = null,
  searchText: String? = null
) {

  val searchText: String? = searchText?.trim()?.takeIf { it.isNotEmpty() }

  internal val isValid: Boolean
    get() = OTT_PATTERN.matches(oneTimeToken) &&
      (entryPoint == null || ENTRY_POINT_PATTERN.matches(entryPoint)) &&
      (searchText?.length ?: 0) <= SEARCH_TEXT_MAX_LENGTH &&
      (deviceBinding?.length ?: 0) <= DEVICE_BINDING_MAX_LENGTH

  override fun toString(): String {
    val binding = if (deviceBinding == null) "null" else REDACTED
    return "BlazeLaunchRequest(oneTimeToken=$REDACTED, tenant=$tenant, deviceBinding=$binding, " +
      "entryPoint=$entryPoint, searchText=$searchText)"
  }

  private companion object {
    const val REDACTED = "<redacted>"
    const val SEARCH_TEXT_MAX_LENGTH = 500
    const val DEVICE_BINDING_MAX_LENGTH = 512
    val OTT_PATTERN = Regex("^[\\x20-\\x7E]{16,4096}$")
    val ENTRY_POINT_PATTERN = Regex("^[a-z0-9_]{1,40}$")
  }
}
