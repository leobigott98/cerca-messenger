package com.leobigott.cercamessenger.protocol.cerca

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class CercaRouterEngine(
    private val config: CercaProtocolConfig = CercaProtocolConfig()
) {
    fun updateDirectPredictability(
        oldPrediction: Double,
        lastEncounterAt: Long?,
        now: Long = System.currentTimeMillis()
    ): Double {
        val pEnc = if (lastEncounterAt == null) {
            config.pEncMax
        } else {
            val interval = (now - lastEncounterAt).coerceAtLeast(0L)
            if (interval < config.iTypMillis) {
                config.pEncMax * (interval.toDouble() / config.iTypMillis.toDouble())
            } else {
                config.pEncMax
            }
        }

        return clamp01(oldPrediction + (1.0 - oldPrediction) * pEnc)
    }

    fun updateTransitivePredictability(
        oldPredictionToDestination: Double,
        predictionToPeer: Double,
        peerPredictionToDestination: Double
    ): Double {
        val candidate = predictionToPeer * peerPredictionToDestination * config.beta
        return max(oldPredictionToDestination, clamp01(candidate))
    }

    fun agePrediction(
        currentPrediction: Double,
        elapsedMillis: Long
    ): Double {
        if (elapsedMillis <= 0) return clamp01(currentPrediction)
        val k = elapsedMillis.toDouble() / config.agingUnitMillis.toDouble()
        return clamp01(currentPrediction * config.gamma.pow(k))
    }

    fun utility(context: NodeContext, now: Long = System.currentTimeMillis()): Double {
        val density = if (config.densityNorm <= 0.0) 0.0 else context.smoothedDensity / config.densityNorm
        val infrastructure = infrastructureRecencyRatio(
            hasInternetGateway = context.hasInternetGateway,
            lastInternetAt = context.lastInternetAt,
            now = now
        )

        return clamp01(
            config.wPredictability * context.predictabilityToDestination +
                config.wBattery * context.batteryLevel +
                config.wBuffer * context.bufferFreeRatio +
                config.wDensity * clamp01(density) +
                config.wInfrastructure * infrastructure +
                config.wReputation * clamp01(context.credits / 100.0)
        )
    }

    fun shouldForward(
        self: NodeContext,
        peer: NodeContext,
        message: CercaMessagePayload,
        peerAlreadyHasMessage: Boolean,
        peerAlreadyInPath: Boolean,
        now: Long = System.currentTimeMillis()
    ): ForwardDecision {
        if (peerAlreadyHasMessage) {
            return ForwardDecision(false, utility(self, now), utility(peer, now), "Peer already has message")
        }

        if (peerAlreadyInPath) {
            return ForwardDecision(false, utility(self, now), utility(peer, now), "Peer already in path")
        }

        if (message.ttlExpiresAt <= now) {
            return ForwardDecision(false, utility(self, now), utility(peer, now), "Message expired")
        }

        if (message.destinationScope == "PUBLIC_BROADCAST") {
            if (message.copiesLeft <= 1) {
                return ForwardDecision(false, utility(self, now), utility(peer, now), "Public broadcast has no copies left")
            }
            val crisisScore = clamp01(message.crisisPriority / 5.0)
            val peerUtility = clamp01(
                utility(peer, now) +
                    config.wCrisisPriority * crisisScore +
                    config.wVolunteerMode * if (peer.nodeMode == "VOLUNTEER") 1.0 else 0.0 +
                    config.wGatewayMode * if (peer.nodeMode == "GATEWAY" || peer.hasInternetGateway) 1.0 else 0.0 +
                    0.25
            )
            return ForwardDecision(true, utility(self, now), peerUtility, "Public broadcast epidemic-style forwarding")
        }

        if (message.destinationId == peer.nodeId) {
            return ForwardDecision(true, 1.0, 1.0, "Direct delivery")
        }

        val crisisScore = clamp01(message.crisisPriority / 5.0)
        val gatewayModeScore = if (peer.nodeMode == "GATEWAY" || peer.hasInternetGateway) 1.0 else 0.0
        val volunteerModeScore = if (peer.nodeMode == "VOLUNTEER") 1.0 else 0.0
        val verificationScore = when (message.verificationStatus) {
            "CONFIRMED_BY_AUTHORITY" -> 1.0
            "CONFIRMED_BY_VOLUNTEER" -> 0.8
            "SEEN_BY_MULTIPLE_NODES" -> 0.5
            "RESOLVED", "DUPLICATE", "DISCARDED" -> -1.0
            else -> 0.0
        }

        if (message.isEmergency || message.crisisPriority >= 4) {
            val selfUtility = utility(self, now)
            val peerUtility = clamp01(
                utility(peer, now) +
                    config.wCrisisPriority * crisisScore +
                    config.wGatewayMode * gatewayModeScore +
                    config.wVolunteerMode * volunteerModeScore +
                    config.wVerification * verificationScore
            )
            return ForwardDecision(true, selfUtility, peerUtility, "Crisis priority forwarding")
        }

        if (message.copiesLeft <= 1) {
            return ForwardDecision(false, utility(self, now), utility(peer, now), "No copies left")
        }

        val selfUtility = utility(self, now)
        val peerUtility = clamp01(
            utility(peer, now) +
                config.wCrisisPriority * crisisScore +
                config.wGatewayMode * gatewayModeScore +
                config.wVolunteerMode * volunteerModeScore +
                config.wVerification * verificationScore
        )

        return if (peerUtility > selfUtility + config.utilityMargin) {
            ForwardDecision(true, selfUtility, peerUtility, "Peer utility is higher")
        } else {
            ForwardDecision(false, selfUtility, peerUtility, "Peer utility is not better")
        }
    }

    fun forwardingPriority(
        self: NodeContext,
        peer: NodeContext,
        message: CercaMessagePayload,
        peerAlreadyHasMessage: Boolean,
        peerAlreadyInPath: Boolean,
        messageSizeBytes: Int = 0,
        now: Long = System.currentTimeMillis()
    ): ForwardDecision {
        return shouldForward(
            self = self,
            peer = peer,
            message = message,
            peerAlreadyHasMessage = peerAlreadyHasMessage,
            peerAlreadyInPath = peerAlreadyInPath,
            now = now
        )
    }

    fun senderCopiesAfterForward(currentCopies: Int): Int {
        return max(1, currentCopies / 2)
    }

    fun receiverCopiesAfterForward(currentCopies: Int): Int {
        return max(1, ceil(currentCopies / 2.0).toInt())
    }

    fun infrastructureRecencyRatio(
        hasInternetGateway: Boolean,
        lastInternetAt: Long?,
        now: Long = System.currentTimeMillis()
    ): Double {
        if (hasInternetGateway) return 1.0
        if (lastInternetAt == null) return 0.0
        val elapsed = (now - lastInternetAt).coerceAtLeast(0L)
        return clamp01(1.0 - elapsed.toDouble() / config.internetDecayMillis.toDouble())
    }

    fun smoothDensity(previous: Double, currentNearbyCount: Int, alpha: Double = 0.2): Double {
        return alpha * currentNearbyCount + (1.0 - alpha) * previous
    }

    private fun clamp01(value: Double): Double = min(1.0, max(0.0, value))
}
