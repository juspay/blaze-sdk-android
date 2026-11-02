package `in`.breeze.blaze

internal enum class BlazeErrorCodes {
  NOT_INITIALISED,
  ;

  val code: String get() = "SDK_$name"
}
