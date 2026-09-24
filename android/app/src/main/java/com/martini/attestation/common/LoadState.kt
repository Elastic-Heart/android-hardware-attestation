package com.martini.attestation.common

sealed interface LoadState<out L, out R> {
    data object Loading : LoadState<Nothing, Nothing>

    data object Initial : LoadState<Nothing, Nothing>

    data class Failure<R>(val error: R) : LoadState<Nothing, R>

    data class Success<L>(val data: L) : LoadState<L, Nothing>
}
