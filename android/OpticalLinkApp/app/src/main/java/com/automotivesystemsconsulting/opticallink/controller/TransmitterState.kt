package com.automotivesystemsconsulting.opticallink.controller

/**
 * Represents the operating state of the optical transmitter state machine.
 */
enum class TransmitterState {
    /** Controller initialized and ready to transmit. */
    IDLE,

    /** Optical transmission is actively in progress. */
    TRANSMITTING,

    /** Optical transmission was explicitly stopped. */
    STOPPED,

    /** Optical transmission encountered an error (e.g. invalid input). */
    ERROR
}
