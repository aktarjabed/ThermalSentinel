package com.thermalsentinel.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Guards the navigation contract. Fails the build if a route is added to
 * Routes without a matching drawer entry, or if the drawer gains a duplicate
 * route. Runs as a JVM test; no device required.
 */
class RouteContractTest {

    private fun routesConstants(): Set<String> =
        Routes::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
            .toSet()

    @Test
    fun everyRouteConstantHasAnEntryInAllDestinations() {
        val declared = routesConstants()
        val drawerRoutes = allDestinations.map { it.route }.toSet()
        val missing = declared - drawerRoutes
        val extra = drawerRoutes - declared
        assertTrue("Routes constants without a drawer entry: $missing", missing.isEmpty())
        assertTrue("Drawer entries without a Routes constant: $extra", extra.isEmpty())
    }

    @Test
    fun everyRouteAppearsExactlyOnceInAllDestinations() {
        val routes = allDestinations.map { it.route }
        assertEquals("Duplicate route in allDestinations", routes.size, routes.toSet().size)
    }
}
