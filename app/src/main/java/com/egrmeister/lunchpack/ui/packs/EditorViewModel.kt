package com.egrmeister.lunchpack.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.ItemDraft
import com.egrmeister.lunchpack.domain.ItemIcon
import com.egrmeister.lunchpack.domain.Limits
import com.egrmeister.lunchpack.domain.TemplateDraft
import com.egrmeister.lunchpack.domain.TemplateError
import com.egrmeister.lunchpack.domain.TemplateSaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** One editable row; [key] is a stable UI key, [id] the stored item ID (null for new items). */
data class EditableItem(
    val key: String,
    val id: String?,
    val name: String,
    val note: String,
    val compartment: Compartment,
    val icon: ItemIcon,
)

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val missing: Boolean = false,
    val name: String = "",
    val favorite: Boolean = false,
    val items: List<EditableItem> = emptyList(),
    val errors: List<TemplateError> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val dirty: Boolean = false,
) {
    val canAddItem: Boolean get() = items.size < Limits.MAX_ITEMS
}

class EditorViewModel(private val container: AppContainer, private val templateId: Long?) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** Snapshot of the loaded values, used to detect unsaved edits. */
    private var original: Triple<String, Boolean, List<EditableItem>> = Triple("", false, emptyList())

    init {
        viewModelScope.launch {
            if (templateId == null) {
                val first = blankItem()
                original = Triple("", false, listOf(first))
                _state.value = EditorUiState(loading = false, isNew = true, items = listOf(first))
            } else {
                val template = container.repository.templateFlow(templateId).first()
                if (template == null) {
                    _state.value = EditorUiState(loading = false, isNew = false, missing = true)
                } else {
                    val items = template.items.sortedBy { it.displayOrder }.map {
                        EditableItem(it.id, it.id, it.name, it.note, it.compartment, it.icon)
                    }
                    original = Triple(template.name, template.favorite, items)
                    _state.value = EditorUiState(
                        loading = false,
                        isNew = false,
                        name = template.name,
                        favorite = template.favorite,
                        items = items,
                    )
                }
            }
        }
    }

    private fun blankItem() = EditableItem(
        key = UUID.randomUUID().toString(),
        id = null,
        name = "",
        note = "",
        compartment = Compartment.MAIN,
        icon = ItemIcon.GENERIC,
    )

    private fun edit(transform: (EditorUiState) -> EditorUiState) {
        _state.update { current ->
            val next = transform(current)
            next.copy(dirty = Triple(next.name, next.favorite, next.items) != original)
        }
    }

    fun setName(value: String) = edit { it.copy(name = value.take(Limits.PACK_NAME_MAX + 10)) }

    fun setFavorite(value: Boolean) = edit { it.copy(favorite = value) }

    fun addItem() = edit { if (it.canAddItem) it.copy(items = it.items + blankItem()) else it }

    fun removeItem(key: String) = edit { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    fun updateItem(key: String, transform: (EditableItem) -> EditableItem) =
        edit { s -> s.copy(items = s.items.map { if (it.key == key) transform(it) else it }) }

    fun move(key: String, delta: Int) = edit { s ->
        val index = s.items.indexOfFirst { it.key == key }
        val target = index + delta
        if (index < 0 || target !in s.items.indices) {
            s
        } else {
            val list = s.items.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            s.copy(items = list)
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.missing) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = TemplateDraft(
                id = templateId,
                name = current.name,
                favorite = current.favorite,
                items = current.items.map { ItemDraft(it.id, it.name, it.note, it.compartment, it.icon) },
            )
            when (val result = container.service.saveTemplate(draft)) {
                is TemplateSaveResult.Saved -> _state.update { it.copy(saving = false, saved = true, errors = emptyList()) }
                is TemplateSaveResult.Invalid -> _state.update { it.copy(saving = false, errors = result.errors) }
                TemplateSaveResult.Missing -> _state.update { it.copy(saving = false, missing = true) }
            }
        }
    }
}
