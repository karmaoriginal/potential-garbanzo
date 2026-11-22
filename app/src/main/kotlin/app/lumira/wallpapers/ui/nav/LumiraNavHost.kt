package app.lumira.wallpapers.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lumira.wallpapers.ui.screens.detail.DetailScreen
import app.lumira.wallpapers.ui.screens.home.HomeScreen
import app.lumira.wallpapers.ui.screens.library.LibraryScreen
import app.lumira.wallpapers.ui.screens.settings.SettingsScreen
import app.lumira.wallpapers.ui.screens.shop.ShopScreen

/** All destinations. Small app, so a string enum keeps navigation explicit. */
object Routes {
    const val HOME = "home"
    const val SHOP = "shop"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{kind}/{id}"
    const val SHOP_DETAIL = "shop_detail/{id}"

    fun detail(kind: String, id: String) = "detail/$kind/$id"
    fun shopDetail(id: String) = "shop_detail/$id"
}

@Composable
fun LumiraNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.HOME,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInVertically(animationSpec = tween(380)) { it / 14 } + fadeIn(tween(260))
        },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = {
            slideOutVertically(animationSpec = tween(320)) { it / 14 } + fadeOut(tween(200))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenShop = { navController.navigateToTab(Routes.SHOP) },
                onOpenLibrary = { navController.navigateToTab(Routes.LIBRARY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDetail = { kind, id -> navController.navigate(Routes.detail(kind, id)) },
            )
        }
        composable(Routes.SHOP) {
            ShopScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { id -> navController.navigate(Routes.shopDetail(id)) },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { kind, id -> navController.navigate(Routes.detail(kind, id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
            enterTransition = {
                scaleIn(initialScale = 0.92f, animationSpec = tween(340)) + fadeIn(tween(240))
            },
            popExitTransition = {
                scaleOut(targetScale = 0.94f, animationSpec = tween(260)) + fadeOut(tween(180))
            },
        ) { entry ->
            DetailScreen(
                wallpaperId = entry.arguments?.getString("id").orEmpty(),
                thumbKey = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.SHOP_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            enterTransition = {
                scaleIn(initialScale = 0.92f, animationSpec = tween(340)) + fadeIn(tween(240))
            },
            popExitTransition = {
                scaleOut(targetScale = 0.94f, animationSpec = tween(260)) + fadeOut(tween(180))
            },
        ) { entry ->
            DetailScreen(
                wallpaperId = null,
                catalogId = entry.arguments?.getString("id"),
                thumbKey = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

@Suppress("unused")
private typealias TransitionScope = AnimatedContentTransitionScope<*>
