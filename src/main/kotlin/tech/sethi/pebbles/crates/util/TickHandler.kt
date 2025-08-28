package tech.sethi.pebbles.crates.util

import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.tick.ServerTickEvent
import tech.sethi.pebbles.crates.PebblesCrate
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS

object TickHandler {
    fun init() {
        FORGE_BUS.addListener<ServerTickEvent.Pre>() {
            processTasks(it.server)
        }
    }

    private fun processTasks(server: MinecraftServer) {
        val currentTick = server.allLevels.first().gameTime

        PebblesCrate.tasks[currentTick]?.let { tasks ->
            for (task in tasks) {
                task.action()
            }
            PebblesCrate.tasks.remove(currentTick) // Remove the executed tasks from the storage
        }
    }
}