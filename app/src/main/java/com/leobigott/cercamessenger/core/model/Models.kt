package com.leobigott.cercamessenger.core.model

enum class CrisisPriority(val level: Int, val label: String) {
    P0_SOS(5, "SOS vital"),
    P1_HEALTH(4, "Salud"),
    P2_REUNIFICATION(3, "Reunificación"),
    P3_LOGISTICS(2, "Logística"),
    P4_COMMUNITY(1, "Comunidad"),
    P5_PERSONAL(0, "Personal")
}

enum class CrisisMessageType(val label: String, val defaultPriority: CrisisPriority) {
    IM_OK("Estoy bien", CrisisPriority.P2_REUNIFICATION),
    NEED_HELP("Necesito ayuda", CrisisPriority.P0_SOS),
    TRAPPED_PEOPLE("Reportar atrapados", CrisisPriority.P0_SOS),
    INJURED_PEOPLE("Reportar heridos", CrisisPriority.P0_SOS),
    MEDICINE("Solicitar medicina", CrisisPriority.P1_HEALTH),
    WATER_FOOD("Solicitar agua/comida", CrisisPriority.P3_LOGISTICS),
    SHELTER("Ubicación de refugio", CrisisPriority.P4_COMMUNITY),
    SUPPLY_POINT("Punto de acopio", CrisisPriority.P4_COMMUNITY),
    FAMILY("Mensaje familiar", CrisisPriority.P2_REUNIFICATION),
    RESCUERS("Mensaje para rescatistas", CrisisPriority.P0_SOS),
    INSTITUTIONAL("Mensaje institucional", CrisisPriority.P4_COMMUNITY),
    PUBLIC_BROADCAST("Anuncio público", CrisisPriority.P4_COMMUNITY),
    PERSONAL("Mensaje personal", CrisisPriority.P5_PERSONAL),
    OTHERS("Otros", CrisisPriority.P3_LOGISTICS)
}

enum class VerificationStatus(val label: String) {
    UNVERIFIED("No verificado"),
    SEEN_BY_MULTIPLE_NODES("Visto por varios nodos"),
    CONFIRMED_BY_VOLUNTEER("Confirmado por voluntario"),
    CONFIRMED_BY_AUTHORITY("Confirmado por autoridad"),
    RESOLVED("Resuelto"),
    DUPLICATE("Duplicado"),
    DISCARDED("Descartado")
}

enum class NodeMode(val label: String) {
    CITIZEN("Ciudadano"),
    VOLUNTEER("Voluntario"),
    GATEWAY("Centro de mando / gateway")
}

enum class DestinationScope(val label: String) {
    DIRECT_CONTACT("Contacto directo"),
    CRISIS_GATEWAY("Gateway de crisis"),
    PUBLIC_BROADCAST("Anuncio público"),
    LOCAL_COMMUNITY("Comunidad local"),
    AUTHORITIES_ONLY("Autoridades")
}

object CrisisConstants {
    const val CRISIS_CONVERSATION_ID = "crisis-broadcast"
    const val CRISIS_DESTINATION_ID = "cerca-crisis-gateway"
    const val PUBLIC_BROADCAST_CONVERSATION_ID = "public-broadcast"
    const val PUBLIC_BROADCAST_DESTINATION_ID = "cerca-public-broadcast"
}

data class DeviceNode(
    val id: String,
    val displayName: String,
    val isNearby: Boolean,
    val batteryLevel: Float,
    val linkQuality: Float,
    val hasInternetGateway: Boolean,
    val nodeMode: NodeMode = NodeMode.CITIZEN,
    val utilityScore: Float? = null,
    val lastSeenText: String
)

enum class MessageStatus {
    QUEUED,
    WAITING_FOR_RELAY,
    SENDING,
    SENT_DIRECT,
    RELAYED,
    DELIVERED,
    EXPIRED,
    FAILED
}

data class OfflineMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val destinationId: String,
    val text: String,
    val timestamp: Long,
    val receivedAt: Long,
    val isFromMe: Boolean,
    val status: MessageStatus,
    val hopCount: Int = 0,
    val copiesLeft: Int = 1,
    val ttlRemainingMinutes: Int? = null,
    val bestRelayName: String? = null,
    val utilityScore: Float? = null,
    val crisisType: CrisisMessageType = CrisisMessageType.PERSONAL,
    val crisisPriority: CrisisPriority = CrisisPriority.P5_PERSONAL,
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val approximateLocation: String? = null,
    val peopleAffected: Int? = null,
    val requiresResponse: Boolean = false,
    val destinationScope: DestinationScope = DestinationScope.DIRECT_CONTACT
)

data class Conversation(
    val id: String,
    val peerName: String,
    val peerId: String,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0,
    val peerIsNearby: Boolean,
    val lastStatus: MessageStatus
)
