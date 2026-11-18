package `in`.breeze.blaze

internal enum class BlazeErrorCodes {
  FRAME_URL_UNAVAILABLE,
  ;

  val code: String get() = "SDK_$name"
}
