package me.tatarka.inject.compiler

private const val OPTION_GENERATE_COMPANION_EXTENSIONS = "me.tatarka.inject.generateCompanionExtensions"

data class Options(
    val generateCompanionExtensions: Boolean
) {
    companion object {
        fun from(map: Map<String, String>) = Options(
            generateCompanionExtensions = map[OPTION_GENERATE_COMPANION_EXTENSIONS]?.toBoolean()
                ?: false
        )
    }
}