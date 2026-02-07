package com.meshsos.data.model

/**
 * Types of emergencies that can be reported
 */
enum class EmergencyType(val displayName: String) {
    MEDICAL("Medical Emergency"),
    FIRE("Fire"),
    FLOOD("Flood"),
    EARTHQUAKE("Earthquake"),
    GENERAL("General Emergency")
}

/**
 * Delivery status of an SOS packet
 */
enum class DeliveryStatus(val displayName: String) {
    PENDING("Pending"),
    RELAYED("Relayed"),
    DELIVERED("Delivered"),
    RESPONDED("Responded")
}

/**
 * Node role in the mesh network
 */
enum class NodeRole(val displayName: String) {
    USER("User"),
    RELAY("Relay"),
    RESPONDER("Responder")
}
