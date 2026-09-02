package io.xireiki.sfa.update

sealed class UpdateCheckException : Exception() {
    class TrackNotSupported : UpdateCheckException()
}
