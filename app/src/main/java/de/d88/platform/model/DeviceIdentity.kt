package de.d88.platform.model

/**
 * D88 Device Identity (Spec README.md, Abschnitt 5).
 *
 * Sensible Hardwarekennungen werden nur verwendet, soweit sie für die Funktion
 * notwendig sind. D88 sammelt keine unnötigen Gerätefingerprints.
 */
data class DeviceIdentity(
    val deviceId: String,
    val deviceKey: String,
    val deviceType: String,
    val platform: String,
    val platformVersion: String,
    val d88Version: String,
    var role: Role,
    val ownerScope: String,
    var trustState: TrustState,
    var pairingState: PairingState,
    var authenticationState: AuthState,
    var capabilities: Set<Capability>,
    val projectScopes: Set<String>,
    val taskScopes: Set<String>,
    var dataScopes: Set<DataScope>,
    var networkState: NetworkState,
    var debugScope: DebugScope,
    var sessionScopes: Set<String>,
    val policyVersion: Int,
    val simulated: Boolean,
    var createdAt: Long,
    var updatedAt: Long
) {
    fun copyUpdated(clock: () -> Long = System::currentTimeMillis): DeviceIdentity =
        copy(updatedAt = clock())
}
