# Error codes

Error codes the SDK can send in `payload.errorCode`.

| Code | When | What to do | Retry as is? |
| --- | --- | --- | --- |
| `SDK_INVALID_ALLOWED_ORIGIN` | An entry in `allowedOrigins` in `sdk.json` is not an https origin with no path, so no page loads. Sent in the `initiate` answer, and in answer to every later `process` | Fix `allowedOrigins` in `sdk.json`, call `terminate`, then `initiate` again | No |
| `SDK_FRAME_URL_UNAVAILABLE` | The SDK could not read `sdk.json`, or it has no https address for your `environment`, so no page loads. Sent in the `initiate` answer, and in answer to every later `process`. `errorDetails` says why | Check the network and `environment`, call `terminate`, then `initiate` again | Yes, after `terminate` |
