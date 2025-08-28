package tech.sethi.pebbles.crates

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.lootcrates.CrateEventHandler
import tech.sethi.pebbles.crates.particles.CrateParticles
import tech.sethi.pebbles.crates.screenhandlers.PrizeDisplayScreenHandlerFactory
import tech.sethi.pebbles.crates.util.*
import tech.sethi.pebbles.crates.commands.CrateCommand
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.*

@Mod(PebblesCrate.MOD_ID)
object PebblesCrate {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    const val MOD_ID = "pebbles_crate"
    val cratesInUse = Collections.synchronizedSet(mutableSetOf<BlockPos>())
    val playerCooldowns: MutableMap<UUID, Long> = Collections.synchronizedMap(mutableMapOf())
    val tasks: MutableMap<Long, MutableList<Task>> = mutableMapOf()
    val crateDataManager: CrateDataManager = CrateDataManager();
    val blacklistConfigManager: BlacklistConfigManager = BlacklistConfigManager()
    var server: MinecraftServer? = null

    var nbtOps: RegistryOps<Tag>? = null

    init {
        logger.info("Initializing Pebbles Loot Crates!")

        //create /config/pebbles-crate/crates if it doesn't exist
        CrateConfigManager.createCratesFolder()

        var bus = MOD_BUS

        TickHandler.init()

        bus.addListener<RegisterCommandsEvent> {
            CrateCommand.register(it.dispatcher)
        }

        FORGE_BUS.addListener<PlayerInteractEvent.RightClickBlock> { event ->
            val player = event.entity;
            val world = event.level
            val hand = event.hand
            val hitResult = event.hitVec

            if (world.isClientSide || hand != InteractionHand.MAIN_HAND) {
                return@addListener
            }

            val savedCrateData = crateDataManager.getCrateData()

            // Check if the clicked position is in the crate data
            if (hitResult.blockPos in savedCrateData) {
                var crateName = savedCrateData[hitResult.blockPos]
                val crateConfig = CrateConfigManager.getCrateConfig(crateName!!)

                if (crateConfig != null && crateConfig.screenName != null) {
                    crateName = crateConfig.screenName
                }

                val parsedKey = BuiltInRegistries.ITEM.get(
                    ResourceLocation.tryParse(
                        crateConfig?.crateKey?.material ?: "minecraft:gold_nugget"
                    )
                )
                val parseKeyStack = ItemStack(parsedKey)
                val crateKeyLore = crateConfig?.crateKey?.lore?.map(Component::literal)
                if (crateKeyLore != null) {
                    setLore(parseKeyStack, crateKeyLore)
                }
                if (crateConfig != null) {
                    val nbt = CustomData.of(CompoundTag().apply { putString("CrateName", crateName) })
                    parseKeyStack.set(DataComponents.CUSTOM_DATA, nbt)
                }

                if (crateConfig != null) {
                    val heldStack = player.mainHandItem
                    val heldStackNbt = heldStack.get(DataComponents.CUSTOM_DATA)?.copyTag()
                    if (heldStack.item == parseKeyStack.item && heldStackNbt != null && heldStackNbt.getString(
                            "CrateName"
                        ) == crateConfig.crateName
                    ) {
                        if (cratesInUse.contains(hitResult.blockPos)) {
                            player.displayClientMessage(
                                Component.literal("Someone is already using this crate!").withStyle(ChatFormatting.RED), false
                            )

                            event.cancellationResult = InteractionResult.SUCCESS
                            return@addListener
                        }

                        val crateEventHandler = CrateEventHandler(
                            world,
                            hitResult.blockPos,
                            player as ServerPlayer,
                            crateConfig.prize,
                            cratesInUse,
                            playerCooldowns,
                            crateName
                        )

                        if (crateEventHandler.canOpenCrate()) {
                            heldStack.shrink(1)
                            val finalPrize = crateEventHandler.weightedRandomSelection(crateConfig.prize)
                            crateEventHandler.showPrizesAnimation(finalPrize)
                            crateEventHandler.updatePlayerCooldown()
                        }


                        // Floating item will be spawned in the CrateEventHandler's init block
                    } else {
                        // Open crate preview GUI
                        player.openMenu(
                            PrizeDisplayScreenHandlerFactory(
                                ParseableName("$crateName").returnMessageAsStyledText(), crateConfig
                            )
                        )
                    }

                    event.cancellationResult = InteractionResult.SUCCESS
                    return@addListener
                }
            } else {
                // Assign a new crate if the player is holding a named paper
                val heldStack = player.mainHandItem
                if (heldStack.item == Items.PAPER && heldStack.componentsPatch.get(DataComponents.CUSTOM_NAME) != null && heldStack.get(
                        DataComponents.CUSTOM_DATA)?.copyTag()?.contains("CrateName") == true) {
                    val crateName = heldStack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString("CrateName")

                    if(crateName == null) {
                        event.cancellationResult = InteractionResult.PASS
                        return@addListener
                    }
                    savedCrateData[hitResult.blockPos] = crateName
                    crateDataManager.saveCrateData(savedCrateData)

                    player.displayClientMessage(
                        Component.literal("Assigned a $crateName crate to the block at ${hitResult.blockPos}")
                            .withStyle(ChatFormatting.GRAY), false
                    )

                    event.cancellationResult = InteractionResult.SUCCESS

                    return@addListener
                }
            }

        }

        FORGE_BUS.addListener<BlockEvent.BreakEvent> { event ->
            val player = event.player
            val pos = event.pos

            // Load the saved crate data
            val savedCrateData = crateDataManager.getCrateData()

            // Check if the broken block position is in the crate data
            if (pos in savedCrateData) {
                // Remove the crate data for this position
                savedCrateData.remove(pos)
                crateDataManager.saveCrateData(savedCrateData)

                // Send a message to the player for debugging purposes
                player.displayClientMessage(
                    Component.literal("Crate data removed for position: $pos").withStyle(ChatFormatting.GRAY), false
                )
            }
        }

        FORGE_BUS.addListener<ServerTickEvent.Post> {
            for (world in it.server.allLevels) {
                if (world is ServerLevel) {
                    spawnParticlesForAllCrates(world)
                }
            }
            CrateParticles.updateTimers()
        }

        bus.addListener<ServerStartingEvent> {
            this.server = it.server
            nbtOps = server!!.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        }
    }

    private fun spawnParticlesForAllCrates(world: ServerLevel) {
        val savedCrateData = crateDataManager.getCrateData()
        val blacklist = blacklistConfigManager.getBlacklist()

        for (pos in savedCrateData.keys) {
            // Skip crates in the blacklist
            if (pos in blacklist) continue

            if (world.hasChunkAt(pos.x shr 4, pos.z shr 4)){
                val playersNearby =
                    world.getPlayersByDistance(pos, 16.0) // Only get players within 16 blocks of the crate block
                for (player in playersNearby) {
                    CrateParticles.spawnCrossSpiralsParticles(player, pos, world)
                }
            }
        }
    }


    private fun ServerLevel.getPlayersByDistance(pos: BlockPos, distance: Double): List<ServerPlayer> {
        return this.players().filter { player ->
            player.distanceToSqr(
                Vec3(
                    pos.x + 0.5, pos.y + 0.5, pos.z + 0.5
                )
            ) <= distance * distance
        }
    }
}