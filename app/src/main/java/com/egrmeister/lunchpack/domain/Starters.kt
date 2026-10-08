package com.egrmeister.lunchpack.domain

/**
 * Three editable example packs. They describe what to bring along — they are not meal
 * recommendations. Seeded once on first launch; deleted examples are never restored
 * automatically (only through Settings → Restore starter packs).
 */
object Starters {
    private fun item(name: String, compartment: Compartment, icon: ItemIcon) =
        ItemDraft(id = null, name = name, note = "", compartment = compartment, icon = icon)

    val packs: List<TemplateDraft> = listOf(
        TemplateDraft(
            id = null,
            name = "Everyday Box",
            favorite = false,
            items = listOf(
                item("Main container", Compartment.MAIN, ItemIcon.CONTAINER),
                item("Fruit container", Compartment.SIDES, ItemIcon.FRUIT),
                item("Snack", Compartment.SNACK, ItemIcon.SNACK_POUCH),
                item("Napkin", Compartment.EXTRAS, ItemIcon.NAPKIN),
                item("Spoon", Compartment.EXTRAS, ItemIcon.SPOON),
                item("Water bottle", Compartment.BOTTLE, ItemIcon.BOTTLE),
            ),
        ),
        TemplateDraft(
            id = null,
            name = "Snack Break",
            favorite = false,
            items = listOf(
                item("Snack container", Compartment.SNACK, ItemIcon.CONTAINER),
                item("Napkin", Compartment.EXTRAS, ItemIcon.NAPKIN),
                item("Water bottle", Compartment.BOTTLE, ItemIcon.BOTTLE),
            ),
        ),
        TemplateDraft(
            id = null,
            name = "Outing Pack",
            favorite = false,
            items = listOf(
                item("Main container", Compartment.MAIN, ItemIcon.CONTAINER),
                item("Snack pouch", Compartment.SNACK, ItemIcon.SNACK_POUCH),
                item("Napkin", Compartment.EXTRAS, ItemIcon.NAPKIN),
                item("Cutlery", Compartment.EXTRAS, ItemIcon.CUTLERY),
                item("Water bottle", Compartment.BOTTLE, ItemIcon.BOTTLE),
            ),
        ),
    )
}
