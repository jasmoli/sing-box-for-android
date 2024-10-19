package io.nekohasekai.sfa.constant

import io.nekohasekai.sfa.R

enum class EnabledType(val boolValue: Boolean, keyNameId: Int) {
    Enabled(true, R.string.enabled), Disabled(false, R.string.disabled);

    companion object {
        fun from(value: Boolean): EnabledType {
            return if (value) Enabled else Disabled
        }
        fun getType(value: String, context: Context): EnabledType {
            return if (value == context.getString(R.string.enabled)) Enabled else Disabled
        }
    }
}