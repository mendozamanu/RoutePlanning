# Local FastAPI facade

Start this process in its own PowerShell terminal after OpenTripPlanner is running:

```powershell
.\infra\backend\start.ps1
```

It activates the assumed-current AUCORSA data version and listens only on the host loopback
interface at `http://127.0.0.1:8000`. Android Emulator reaches the same service through its
special host alias `http://10.0.2.2:8000`, which is already the debug default in
`local.defaults.properties`.

No cloud credential is used by FastAPI or OTP. Google Places runs in the Android client and
reads `GOOGLE_MAPS_API` from the ignored root `local.properties` file.
