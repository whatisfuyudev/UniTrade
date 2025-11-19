package com.unitrade.unitrade

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

abstract class BaseFragment<VM : ViewModel> : Fragment() {
    protected inline fun <reified VM : ViewModel> fragmentViewModel(): VM {
        return ViewModelProvider(this).get(VM::class.java)
    }
}
