package `in`.breeze.blaze

internal enum class BlazeErrorCodes {
  INVALID_ALLOWED_ORIGIN,
  FRAME_URL_UNAVAILABLE,
  ;

  val code: String get() = "SDK_$name"
}
