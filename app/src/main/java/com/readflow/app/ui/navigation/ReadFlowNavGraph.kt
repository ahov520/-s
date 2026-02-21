package com.readflow.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.readflow.app.ui.reader.ReaderScreen
import com.readflow.app.ui.reader.ReaderViewModel
import com.readflow.app.ui.shelf.ShelfScreen

@Composable
fun ReadFlowNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.SHELF) {
        composable(Route.SHELF) {
            ShelfScreen(
                onOpenReader = { bookId ->
                    navController.navigate(Route.reader(bookId))
                }
            )
        }

        composable(
            route = Route.READER_PATH,
            arguments = listOf(navArgument(Route.READER_BOOK_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val viewModel: ReaderViewModel = hiltViewModel()
            val bookId = backStackEntry.arguments?.getString(Route.READER_BOOK_ID).orEmpty()
            ReaderScreen(
                bookId = bookId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
