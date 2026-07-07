package com.nattype.tester.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nattype.tester.ui.home.HomeScreen
import com.nattype.tester.ui.result.ResultScreen
import com.nattype.tester.viewmodel.NatTestViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Result : Screen("result")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: NatTestViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onStartTest = {
                    navController.navigate(Screen.Result.route)
                }
            )
        }

        composable(Screen.Result.route) {
            ResultScreen(
                viewModel = viewModel,
                onBackToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
