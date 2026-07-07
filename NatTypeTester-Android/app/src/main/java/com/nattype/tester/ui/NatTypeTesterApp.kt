package com.nattype.tester.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.nattype.tester.ui.navigation.AppNavigation
import com.nattype.tester.viewmodel.NatTestViewModel

@Composable
fun NatTypeTesterApp() {
    val navController = rememberNavController()
    val viewModel: NatTestViewModel = viewModel()

    AppNavigation(
        navController = navController,
        viewModel = viewModel
    )
}
