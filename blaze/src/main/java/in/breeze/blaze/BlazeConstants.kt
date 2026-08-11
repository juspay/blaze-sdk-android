package `in`.breeze.blaze

internal object BlazeConstants {
    val UPI_SCHEMES = setOf(
        "upi", "phonepe", "tez", "gpay", "paytm", "paytmmp", "bhim",
        "amazonpay", "mobikwik", "freecharge", "credpay"
    )

    const val SHARED_PREF_NAME = "BlazeSharedPref"
    const val SDK_CONFIG_PREF_NAME = "BlazeSdkConfigPref"
    const val SDK_CONFIG_URL = "https://sdk.breeze.in/core/sdk.json"
    const val SDK_CONFIG_KEY = "blazeSdkConfig"
    const val SDK_CONFIG_TIMEOUT_MS = 3000
}
