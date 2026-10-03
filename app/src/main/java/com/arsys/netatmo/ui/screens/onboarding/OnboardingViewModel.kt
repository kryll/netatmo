package com.arsys.netatmo.ui.screens.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }

    val onboardingCompleted: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[ONBOARDING_COMPLETED_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markOnboardingCompleted() {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[ONBOARDING_COMPLETED_KEY] = true
            }
        }
    }
}
