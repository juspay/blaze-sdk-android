package `in`.breeze.blaze

sealed class BlazeLaunchResult {
  data class Started(val journeyId: String) : BlazeLaunchResult()
  data class Failed(val error: BlazeLaunchError) : BlazeLaunchResult()
}

enum class BlazeLaunchError {
  E_NOT_INITIALISED,
  E_JOURNEY_IN_PROGRESS,
  E_LAUNCH_INVALID,
  E_CONTENT_LOAD_FAILED,
  E_AUTH_OTT_REJECTED,
  E_AUTH_UNAVAILABLE;

  internal companion object {
    fun fromContentError(errorDetails: String): BlazeLaunchError = when (errorDetails) {
      E_AUTH_OTT_REJECTED.name -> E_AUTH_OTT_REJECTED
      E_AUTH_UNAVAILABLE.name -> E_AUTH_UNAVAILABLE
      else -> E_LAUNCH_INVALID
    }
  }
}
