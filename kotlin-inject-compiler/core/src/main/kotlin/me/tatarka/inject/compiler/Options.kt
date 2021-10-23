package me.tatarka.inject.compiler

private const val OPTION_GENERATE_COMPANION_EXTENSIONS = "me.tatarka.inject.generateCompanionExtensions"
private const val OPTION_ENABLE_JAVAX_ANNOTATIONS = "me.tatarka.inject.enableJavaxAnnotations"
private const val OPTION_USE_CLASS_REFERENCE_FOR_SCOPED_ACCESS = "me.tatarka.inject.useClassReferenceForScopeAccess"
private const val OPTION_DUMP_GRAPH = "me.tatarka.inject.dumpGraph"

data class Options(
    val generateCompanionExtensions: Boolean = false,
    val enableJavaxAnnotations: Boolean = false,
    val useClassReferenceForScopeAccess: Boolean = false,
    val dumpGraph: Boolean = false,
) {
    companion object {
        fun from(map: Map<String, String>) = Options(
            generateCompanionExtensions = map[OPTION_GENERATE_COMPANION_EXTENSIONS]?.toBoolean() ?: false,
            enableJavaxAnnotations = map[OPTION_ENABLE_JAVAX_ANNOTATIONS]?.toBoolean() ?: false,
            useClassReferenceForScopeAccess = map[OPTION_USE_CLASS_REFERENCE_FOR_SCOPED_ACCESS]?.toBoolean() ?: false,
            dumpGraph = map[OPTION_DUMP_GRAPH]?.toBoolean() ?: false,
        )
    }

    fun toMap(): Map<String, String> = mapOf(
        OPTION_GENERATE_COMPANION_EXTENSIONS to generateCompanionExtensions.toString(),
        OPTION_ENABLE_JAVAX_ANNOTATIONS to enableJavaxAnnotations.toString(),
        OPTION_USE_CLASS_REFERENCE_FOR_SCOPED_ACCESS to useClassReferenceForScopeAccess.toString(),
        OPTION_DUMP_GRAPH to dumpGraph.toString(),
    )
}