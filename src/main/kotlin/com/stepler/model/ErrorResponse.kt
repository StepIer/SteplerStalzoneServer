package com.stepler.model

import kotlinx.serialization.Serializable

/** Uniform error body so clients can parse failures the same way as successes. */
@Serializable
data class ErrorResponse(val error: String)
