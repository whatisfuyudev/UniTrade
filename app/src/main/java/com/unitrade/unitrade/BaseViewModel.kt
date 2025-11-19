package com.unitrade.unitrade

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

open class BaseViewModel : ViewModel() {
    val loading = MutableLiveData<Boolean>(false)
    val error = MutableLiveData<String?>()
}
