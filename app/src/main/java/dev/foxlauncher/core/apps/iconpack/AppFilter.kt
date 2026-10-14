package dev.foxlauncher.core.apps.iconpack

/** One start tag of an icon pack's `appfilter.xml`, as the Android side reads it. */
data class XmlTag(val name: String, val attributes: Map<String, String>)

/**
 * What an ADW / Nova icon pack maps (task 7.1), from its `appfilter.xml`: `<item
 * component="ComponentInfo{pkg/cls}" drawable="name"/>` per activity, and for the apps it does not
 * draw, the optional `iconback` / `iconmask` / `iconupon` images and the `scale` the app's own icon
 * is drawn at between them. Components are kept as `ComponentName.flattenToString()` writes them
 * (`pkg/full.Class`), which is how Fox names apps. Pure, so it is unit tested.
 */
data class AppFilter(
    val drawables: Map<String, String>,
    val backs: List<String> = emptyList(),
    val masks: List<String> = emptyList(),
    val upons: List<String> = emptyList(),
    val scale: Float = DEFAULT_SCALE,
) {
    private val byPackage: Map<String, String> =
        drawables.entries.groupBy({ it.key.substringBefore('/') }, { it.value }).mapValues { it.value.first() }

    /** The drawable for [component] (`pkg/cls`): its own entry, else the first of its package. */
    fun drawableFor(component: String): String? =
        drawables[component] ?: byPackage[component.substringBefore('/')]

    /** Whether apps it does not draw get the pack's frame (back, mask or upon) around their own icon. */
    val framesOthers: Boolean get() = backs.isNotEmpty() || masks.isNotEmpty() || upons.isNotEmpty()

    companion object {
        const val DEFAULT_SCALE: Float = 1f

        // Every brace escaped: Android's ICU regex rejects a bare `}` that the JVM's accepts.
        private val COMPONENT_INFO = Regex("""ComponentInfo\{([^/\}]+)/([^\}]+)\}""")

        /**
         * Reads the start tags of `appfilter.xml`; unknown tags and malformed entries are skipped,
         * and so are the items of packages [keep] refuses (a big pack maps tens of thousands of apps).
         */
        fun parse(tags: Sequence<XmlTag>, keep: (packageName: String) -> Boolean = { true }): AppFilter {
            val drawables = LinkedHashMap<String, String>()
            val backs = mutableListOf<String>()
            val masks = mutableListOf<String>()
            val upons = mutableListOf<String>()
            var scale = DEFAULT_SCALE
            for (tag in tags) {
                when (tag.name) {
                    "item" -> {
                        val component = tag.attributes["component"]?.let(::componentOf) ?: continue
                        if (!keep(component.substringBefore('/'))) continue
                        val drawable = tag.attributes["drawable"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                        // The first entry wins, as in ADW and Nova.
                        drawables.putIfAbsent(component, drawable)
                    }
                    "iconback" -> backs += images(tag)
                    "iconmask" -> masks += images(tag)
                    "iconupon" -> upons += images(tag)
                    "scale" -> tag.attributes["factor"]?.toFloatOrNull()?.takeIf { it > 0f && it <= MAX_SCALE }?.let { scale = it }
                }
            }
            return AppFilter(drawables, backs, masks, upons, scale)
        }

        /** `ComponentInfo{pkg/cls}` as `pkg/full.Class`; a class starting with a dot is relative to the package. */
        fun componentOf(raw: String): String? {
            val match = COMPONENT_INFO.find(raw.trim()) ?: return null
            val pkg = match.groupValues[1].trim()
            val cls = match.groupValues[2].trim()
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return "$pkg/${if (cls.startsWith('.')) pkg + cls else cls}"
        }

        /** `img1`, `img2`, … in attribute order of their number. */
        private fun images(tag: XmlTag): List<String> = tag.attributes
            .filterKeys { it.startsWith("img") && it.drop(3).toIntOrNull() != null }
            .toSortedMap(compareBy { it.drop(3).toInt() })
            .values.map(String::trim).filter(String::isNotEmpty)

        private const val MAX_SCALE = 2f
    }
}
