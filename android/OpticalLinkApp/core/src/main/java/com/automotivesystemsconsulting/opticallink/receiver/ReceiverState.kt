package com.automotivesystemsconsulting.opticallink.receiver

/**
 * Operating state of the optical camera receiver state machine.
 */
enum class ReceiverState {
    /** Receiver initialized and idle. */
    IDLE,

    /** Camera active and optical receiver analyzing frames. */
    RECEIVING,

    /** Receiver explicitly stopped. */
    STOPPED,

    /** Camera or receiver processing error occurred. */
    ERROR
}
