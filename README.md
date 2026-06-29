# CERCA Messenger

A thesis-oriented Android offline messaging frontend prototype built with Kotlin + Jetpack Compose.

This codebase is intentionally focused on the frontend and app architecture. The real CERCA routing protocol and Android Nearby Connections API are not implemented yet. Instead, the app uses a `MockProtocolEngine` that simulates nearby nodes and DTN-style message state transitions.

## Current features

- Kotlin + Jetpack Compose UI
- Material 3 dark theme
- Bottom navigation: Chats, Nearby, Settings
- Conversations list with DTN-style message status
- Chat screen with message bubbles
- Mock sending flow:
  - Queued
  - Waiting for relay
  - Relayed
- Nearby devices screen showing CERCA-related metrics:
  - Battery `E(j)`
  - Link quality `Q(i,j)`
  - Internet gateway `I(j)`
  - Utility score `U(j,d)`
- Protocol-ready interface:
  - `ProtocolEngine`
  - `MockProtocolEngine`

## Suggested next steps

1. Open the project in Android Studio.
2. Let Gradle sync.
3. Run the app on an emulator or physical Android phone.
4. Keep improving the UI with mock data first.
5. Later replace `MockProtocolEngine` with a `NearbyProtocolEngine` that uses Android Nearby Connections.
6. Add Room persistence for local messages.
7. Add CERCA routing metadata and message queue management.

## Important architecture idea

The UI should not call Nearby Connections directly.

Instead, future communication logic should be implemented behind this interface:

```kotlin
interface ProtocolEngine {
    fun observeNearbyDevices(): Flow<List<DeviceNode>>
    fun observeMessages(conversationId: String): Flow<List<OfflineMessage>>
    suspend fun sendMessage(conversationId: String, destinationId: String, text: String)
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
}
```

That way, the app can switch from mock protocol simulation to real Nearby Connections without rewriting the frontend.

## Package

`com.leobigott.cercamessenger`
