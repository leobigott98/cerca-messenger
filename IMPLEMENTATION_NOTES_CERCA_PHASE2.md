# CERCA Messenger Phase 2 implementation notes

This patch implements the requested app-side protocol improvements while preserving the current Compose UX as much as possible.

## Implemented

1. **Room-backed conversations**
   - Conversations are now derived from `DtnMessageEntity + PeerEntity + ContactEntity` through `ConversationProjection`.
   - `ConversationsViewModel` no longer uses mock conversations.

2. **QR contacts + public-key storage**
   - Added `ContactEntity` and `ContactDao`.
   - Added a Contacts tab with:
     - a generated local CERCA QR containing node ID + RSA public key,
     - QR scanner to add contacts,
     - saved contact list,
     - tap contact to open encrypted chat.

3. **Hybrid encryption for outgoing contact messages**
   - Added Android Keystore RSA key generation.
   - Added RSA-OAEP key wrapping + AES-GCM message encryption.
   - Network payloads carry encrypted content; relay nodes store/forward encrypted payloads only.
   - The final recipient decrypts locally with the private key.

4. **Better ACK handling**
   - ACKs are now durable Room entities with metadata.
   - ACK summaries are piggybacked in `SUMMARY` envelopes.
   - ACK processing marks own messages as delivered and deletes relayed copies.
   - ACK cache is pruned to the configured limit.

5. **Discovery lifecycle refinement**
   - Nearby discovery/advertising is moved into `NearbyLifecycleService`.
   - The app starts the foreground service after permissions are granted.
   - `NearbyViewModel` observes peers but no longer starts/stops Nearby directly.

## Notes

- Firestore sync is intentionally left for a later phase.
- I could not run a full Gradle compile in this environment because Gradle wrapper download requires internet access.
- Android Studio may suggest dependency updates or small permission adjustments depending on the test device and target SDK.
