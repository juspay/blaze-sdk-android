# Error codes

Error codes the SDK can send in `payload.errorCode`.

| Code | When | What to do | Retry as is? |
| --- | --- | --- | --- |
| `SDK_NOT_INITIALISED` | `process` was called before `initiate` had set the SDK up | Call `process` again | Yes |
| `SER_PAYLOAD_INVALID` | The `initiate` or `process` payload could not be read, or the `initiate` payload has no `publisherId`. `errorDetails` says which | Fix the payload and call again | No |
