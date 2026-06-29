package com.leobigott.cercamessenger.protocol.cerca

data class CercaProtocolConfig(
    val initialCopies: Int = 20,
    val messageTtlMillis: Long = 12 * 60L * 60L * 1000L,
    val utilityMargin: Double = 0.02,
    val ackMaxSize: Int = 500,
    val densityNorm: Double = 10.0,
    val internetDecayMillis: Long = 60L * 60L * 1000L,
    val pEncMax: Double = 0.5,
    val iTypMillis: Long = 30L * 60L * 1000L,
    val beta: Double = 0.9,
    val gamma: Double = 0.999885791,
    val agingUnitMillis: Long = 30_000L,
    val wPredictability: Double = 0.40,
    val wBattery: Double = 0.15,
    val wBuffer: Double = 0.15,
    val wDensity: Double = 0.15,
    val wInfrastructure: Double = 0.10,
    val wReputation: Double = 0.05,
    val wCrisisPriority: Double = 0.30,
    val wGatewayMode: Double = 0.20,
    val wVolunteerMode: Double = 0.10,
    val wVerification: Double = 0.05
)
