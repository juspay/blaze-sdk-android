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

fun getEnvironment(payload: JSONObject): String {
    val environment = payload.optJSONObject("payload")?.optString("environment")
    return if (environment.isNullOrBlank()) "release" else environment
}

fun getService(payload: JSONObject): String {
    return payload.optString("service")
}

fun getBaseUrl(payload: JSONObject): String {
    val environment = getEnvironment(payload)
    return if (environment == "smbBeta") {
        "https://app.beta.v2.breezesdk.store"
    } else if (environment == "smbRelease") {
        "https://app.v2.breezesdk.store"
    } else if (environment == "beta") {
        "https://app.beta.v2.breeze.in"
    } else {
        "https://app.v2.breeze.in"
    }
}

internal fun sdkErrorResponse(
    request: JSONObject,
    eventName: String,
    error: BlazeErrorCodes,
    errorDetails: String
): JSONObject {
    val payload = JSONObject()
        .put("eventName", eventName)
        .put("action", JSONObject.NULL)
        .put("status", JSONObject.NULL)
        .put("methodName", JSONObject.NULL)
        .put("params", JSONObject.NULL)
        .put("event", JSONObject.NULL)
        .put("error", true)
        .put("errorDetails", errorDetails)
        .put("errorCode", error.code)
    return JSONObject()
        .put("requestId", request.optString("requestId"))
        .put("service", getService(request))
        .put("payload", payload)
}

fun isUPIIntentUri(uri: Uri): Boolean {
    if (!uri.isHierarchical) {
        return false
    }
    val scheme = uri.scheme?.lowercase() ?: return false
    val queryParams = uri.queryParameterNames.associateBy(
        keySelector = { name -> name.lowercase() },
        valueTransform = { name -> uri.getQueryParameter(name).orEmpty() })

    val hasPayeeAddress = queryParams["pa"]?.isNotBlank() == true
    val hasPayeeName = queryParams["pn"]?.isNotBlank() == true
    val hasAmount = queryParams["am"]?.isNotBlank() == true
    val isKnownScheme = scheme in BlazeConstants.UPI_SCHEMES

    return hasPayeeAddress && hasPayeeName && hasAmount && isKnownScheme
}
