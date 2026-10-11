package com.automotivesystemsconsulting.opticallink.model

/**
 * Operating mode of the optical transmitter.
 */
enum class TransmissionMode {
    /**
     * Temporal On-Off Keying (A0-TX1 baseline): Flashes the full display area (1 bit per symbol).
     */
    TEMPORAL_OOK,

    /**
     * Spatial Grid Transmission (A0-TX2): Renders an 8x8 matrix of cells transmitting multiple bits simultaneously.
     */
    SPATIAL_GRID
}
