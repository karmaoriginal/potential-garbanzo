package app.lumira.wallpapers.domain.model

/** A top-level shop category as published in catalog.json. */
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val count: Int = 0,
)

/** Sort/filter operators available in the shop. */
enum class ShopSort(val label: String) {
    NEWEST("Recientes"),
    POPULAR("Populares"),
    TRENDING("Tendencia"),
    NAME("A-Z"),
}

enum class MediaFilter(val label: String) {
    ALL("Todo"),
    STATIC("Estáticos"),
    ANIMATED("Animados"),
}
