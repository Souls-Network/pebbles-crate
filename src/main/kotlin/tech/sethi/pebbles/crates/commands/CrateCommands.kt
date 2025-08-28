package tech.sethi.pebbles.crates.commands

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateTransformer
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.ActiveCrateList
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.CrateListScreenHandler
import tech.sethi.pebbles.crates.util.ParseableMessage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object CrateCommand {
    var EXECUTOR_PEBBLES_CRATE = Executors.newFixedThreadPool(1, ThreadFactoryBuilder()
        .setNameFormat("Executor-PebblesCrate-%d")
        .setDaemon(true)
        .build()) as Executor

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val padminCommand = literal("padmin").requires { source ->
            val player = source.player as? Player
            player != null && (source.hasPermission(2) || isLuckPermsPresent() && getLuckPermsApi()?.userManager?.getUser(
                player.uuid
            )!!.cachedData.permissionData.checkPermission("pebbles.admin.crate").asBoolean()) || source.entity == null
        }

        val crateCommand = literal("crate").requires { source ->
            val player = source.player as? Player
            player != null && (source.hasPermission(2) || isLuckPermsPresent() && getLuckPermsApi()?.userManager?.getUser(
                player.uuid
            )!!.cachedData.permissionData.checkPermission("pebbles.admin.crate")
                .asBoolean()) || source.entity == null
        }.executes { context ->
            val source = context.source

            // Open the crate UI
            source.player?.openMenu(SimpleMenuProvider({ syncId, _, p ->
                CrateListScreenHandler(syncId, p)
            }, Component.literal("Crate Management")))

            1
        }

        val getCrateCommand = literal("getcrate").then(
            Commands.argument("crateName", StringArgumentType.greedyString())
                .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                .executes { context -> getCrate(context) },
        )

        val giveKeyCommand = literal("givekey").then(
            Commands.argument("player", EntityArgument.players()).then(
                Commands.argument("amount", IntegerArgumentType.integer(1))
                    .then(Commands.argument("crateName", StringArgumentType.greedyString())
                        .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                        .executes { context -> giveCrateKey(context) })
            )
        )


        val activeCrateConfigCommand = literal("activecrates").executes { context ->
            val source = context.source

            source.player?.openMenu(SimpleMenuProvider({ syncId, _, p ->
                ActiveCrateList(syncId, p)
            }, Component.literal("Blacklist Particles")))

            1
        }

        val reloadCommand = literal("reload").executes { context ->
            val source = context.source
            val crateConfigManager = CrateConfigManager
            crateConfigManager.loadCrateConfigs()
            PebblesCrate.crateDataManager.CRATE_DATA.clear()
            ParseableMessage("Reloaded crate configs", source.player, "placeholder").send()
            1
        }

        // Register the commands
        dispatcher.register(
            padminCommand.then(crateCommand).then(getCrateCommand).then(giveKeyCommand).then(activeCrateConfigCommand).then(reloadCommand)
        )
    }

    private fun isLuckPermsPresent(): Boolean {
        return try {
            Class.forName("net.luckperms.api.LuckPerms")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun getLuckPermsApi(): LuckPerms? {
        return try {
            LuckPermsProvider.get()
        } catch (e: IllegalStateException) {
            null
        }
    }

    private fun getCrateNameSuggestions(
        context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val crateConfigManager = CrateConfigManager
        val crateNames = crateConfigManager.loadCrateConfigs().map { it.crateName }
        return SharedSuggestionProvider.suggest(crateNames, builder)
    }

    private fun getCrate(context: CommandContext<CommandSourceStack>): Int {
        val crateName = StringArgumentType.getString(context, "crateName")
        val crateTransformer = CrateTransformer(crateName, context.source.player as Player)

        crateTransformer.giveTransformer()
        return 1
    }


    private fun giveCrateKey(context: CommandContext<CommandSourceStack>): Int {
        CompletableFuture.runAsync( { ->
            val crateName = StringArgumentType.getString(context, "crateName")
            val amount = IntegerArgumentType.getInteger(context, "amount")
            val players = EntityArgument.getPlayers(context, "player")

            for (player in players) {
                if (player == null) continue
                CrateTransformer(crateName, player).giveKey(amount, player)
                val adminMessage = "${player.name.string} received $amount $crateName keys!"

                if (context.source.player != null) {
                    ParseableMessage(adminMessage, context.source.player, "placeholder").send()
                    println(adminMessage)
                }

                println(adminMessage)

            }
        }, EXECUTOR_PEBBLES_CRATE)

        return 1
    }


}
