package com.unitrade.unitrade

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
): BaseViewModel() {

    val currentUser = MutableLiveData<FirebaseUser?>().apply {
        value = repo.getCurrentUser()
    }

    fun register(email: String, password: String, name: String, extra: Map<String, Any>? = null) {
        viewModelScope.launch {
            loading.value = true
            when (val res = repo.register(email, password, name, extra)) {
                is Result.Success -> {
                    currentUser.value = res.data
                    error.value = null
                }
                is Result.Error -> error.value = res.exception.message
                Result.Loading -> TODO()
            }
            loading.value = false
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            loading.value = true
            when (val res = repo.login(email, password)) {
                is Result.Success -> {
                    currentUser.value = res.data
                    error.value = null
                }
                is Result.Error -> error.value = res.exception.message
                Result.Loading -> TODO()
            }
            loading.value = false
        }
    }

    fun logout() {
        repo.logout()
        currentUser.value = null
    }
}
