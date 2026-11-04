# Error codes

Error codes the SDK can send in `payload.errorCode`.

| Code | When | What to do | Retry as is? |
| --- | --- | --- | --- |
| `SDK_FRAME_URL_UNAVAILABLE` | The assistant can't load for your `service` and `environment`, so no page loads. Sent in the `initiate` answer, and in answer to every later `process` | Check `environment`. Calling `terminate` and `initiate` again in the same app session gives the same answer; a fixed config is picked up the next time the app starts. If it keeps happening, contact the Breeze team | No, not in the same app session |
