package dev.foxlauncher.feature.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.core.common.Collation
import dev.foxlauncher.core.common.DefaultDispatcher
import dev.foxlauncher.feature.widgets.app.AppWidgetCatalog
import dev.foxlauncher.feature.widgets.app.AppWidgetGroup
import dev.foxlauncher.feature.widgets.app.AppWidgetProviders
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The widget picker's app widgets tab (task 6.2): installed providers, grouped by app. */
@HiltViewModel
class WidgetPickerViewModel @Inject constructor(
    private val providers: AppWidgetProviders,
    @param:DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    private val _appWidgets = MutableStateFlow<ImmutableList<AppWidgetGroup>?>(null)

    /** Null until the first [reload] finishes. */
    val appWidgets: StateFlow<ImmutableList<AppWidgetGroup>?> = _appWidgets.asStateFlow()

    private var loading: Job? = null

    /** On every opening of the picker: apps may have come and gone since the last. */
    fun reload() {
        loading?.cancel()
        loading = viewModelScope.launch {
            val options = providers.load()
            _appWidgets.value = withContext(default) {
                AppWidgetCatalog.group(options, Collation.labelComparator()).toImmutableList()
            }
        }
    }
}
