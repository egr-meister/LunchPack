package com.egrmeister.lunchpack.domain

data class ItemDraft(
    val id: String?,
    val name: String,
    val note: String,
    val compartment: Compartment,
    val icon: ItemIcon,
)

data class TemplateDraft(
    val id: Long?,
    val name: String,
    val favorite: Boolean,
    val items: List<ItemDraft>,
)

sealed interface TemplateError {
    val message: String

    data object PackNameBlank : TemplateError {
        override val message = "Enter a pack name."
    }

    data object PackNameTooLong : TemplateError {
        override val message = "Pack names can be up to ${Limits.PACK_NAME_MAX} characters."
    }

    data object DuplicatePackName : TemplateError {
        override val message = "Another pack already uses this name."
    }

    data object NoItems : TemplateError {
        override val message = "Add at least one item."
    }

    data object TooManyItems : TemplateError {
        override val message = "A pack can hold up to ${Limits.MAX_ITEMS} items."
    }

    data object TooManyTemplates : TemplateError {
        override val message = "You can keep up to ${Limits.MAX_TEMPLATES} packs."
    }

    data class ItemNameBlank(val index: Int) : TemplateError {
        override val message get() = "Item ${index + 1}: enter a name."
    }

    data class ItemNameTooLong(val index: Int) : TemplateError {
        override val message get() = "Item ${index + 1}: names can be up to ${Limits.ITEM_NAME_MAX} characters."
    }

    data class NoteTooLong(val index: Int) : TemplateError {
        override val message get() = "Item ${index + 1}: notes can be up to ${Limits.NOTE_MAX} characters."
    }
}

object TemplateValidator {
    /**
     * @param existing (id, name) of every stored template; the edited template itself is ignored
     *        for the duplicate-name check.
     * @param templateCount number of stored templates (used only when creating a new one).
     */
    fun validate(draft: TemplateDraft, existing: List<Pair<Long, String>>, templateCount: Int): List<TemplateError> {
        val errors = mutableListOf<TemplateError>()
        val name = draft.name.trim()
        when {
            name.isEmpty() -> errors += TemplateError.PackNameBlank
            name.length > Limits.PACK_NAME_MAX -> errors += TemplateError.PackNameTooLong
            existing.any { (id, other) -> id != draft.id && other.trim().equals(name, ignoreCase = true) } ->
                errors += TemplateError.DuplicatePackName
        }
        if (draft.id == null && templateCount >= Limits.MAX_TEMPLATES) errors += TemplateError.TooManyTemplates
        if (draft.items.isEmpty()) errors += TemplateError.NoItems
        if (draft.items.size > Limits.MAX_ITEMS) errors += TemplateError.TooManyItems
        draft.items.forEachIndexed { index, item ->
            val itemName = item.name.trim()
            if (itemName.isEmpty()) errors += TemplateError.ItemNameBlank(index)
            if (itemName.length > Limits.ITEM_NAME_MAX) errors += TemplateError.ItemNameTooLong(index)
            if (item.note.trim().length > Limits.NOTE_MAX) errors += TemplateError.NoteTooLong(index)
        }
        return errors
    }

    /**
     * A name not used by any of [taken] (case-insensitive): "Everyday Box", "Everyday Box (2)", …
     * Always within [Limits.PACK_NAME_MAX] characters.
     */
    fun uniqueName(base: String, taken: Collection<String>): String {
        val lower = taken.map { it.trim().lowercase() }.toSet()
        val trimmed = base.trim().take(Limits.PACK_NAME_MAX)
        if (trimmed.lowercase() !in lower) return trimmed
        return generateSequence(2) { it + 1 }
            .map { n ->
                val suffix = " ($n)"
                trimmed.take(Limits.PACK_NAME_MAX - suffix.length).trimEnd() + suffix
            }
            .first { it.lowercase() !in lower }
    }
}
