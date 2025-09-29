package `in`.breeze.blaze

import android.net.Uri
import org.json.JSONObject

fun safeParseJson(jsonString: String): JSONObject {
    return try {
        JSONObject(jsonString)
    } catch (e: Exception) {
        JSONObject()
    }
}

fun getBaseUrl(payload: JSONObject): String {
    val environment = payload.optJSONObject("payload")?.optString("environment") ?: "release"
    return if (environment == "smbBeta") {
        "https://app.beta.breezesdk.store"
    } else if (environment == "smbRelease") {
        "https://app.breezesdk.store"
    } else if (environment == "beta") {
        "https://app.beta.breeze.in"
    } else {
        "https://app.breeze.in"
    }
}

fun isUPIIntentUri(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    val queryParams = uri.queryParameterNames.associateBy(
        keySelector = { name -> name.lowercase() },
        valueTransform = { name -> uri.getQueryParameter(name).orEmpty() })

    val hasPayeeAddress = queryParams["pa"]?.isNotBlank() == true
    val hasPayeeName = queryParams["pn"]?.isNotBlank() == true
    val hasCurrency = queryParams["cu"]?.isNotBlank() == true
    val hasAmount = queryParams["am"]?.isNotBlank() == true
    val isKnownScheme = scheme in BlazeConstants.UPI_SCHEMES

    return hasPayeeAddress && hasPayeeName && hasCurrency && hasAmount && isKnownScheme
}
