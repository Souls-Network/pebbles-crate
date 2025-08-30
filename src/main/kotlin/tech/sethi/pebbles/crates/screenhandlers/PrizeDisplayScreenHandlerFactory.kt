package tech.sethi.pebbles.crates.screenhandlers

import ca.landonjw.gooeylibs2.api.page.GooeyPage
import ca.landonjw.gooeylibs2.api.page.LinkedPage
import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.Prize
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.ParseableName
import tech.sethi.pebbles.crates.util.setLore

class PrizeDisplayScreenHandlerFactory(private val title: Component, private val crateConfig: CrateConfig) :
    MenuProvider {
    override fun createMenu(syncId: Int, inv: Inventory, player: Player): AbstractContainerMenu {
        var currentPage = 0

        val crateItems = crateConfig.prize
        val handler = object : GenericChestMenu(
            MenuType.GENERIC_9x6, syncId, inv, CrateInventory(crateItems, currentPage), 6
        ) {
            override fun clicked(slotNumber: Int, button: Int, action: ClickType, playerEntity: Player
            ) {
                if (slotNumber == 45) { // Previous page arrow
                    if (currentPage > 0) {
                        currentPage--
                        (this.container as CrateInventory).populateInventory(crateItems, currentPage)
                    }
                } else if (slotNumber == 53) { // Next page arrow
                    if (currentPage < (crateItems.size - 1) / 45) {
                        currentPage++
                        (this.container as CrateInventory).populateInventory(crateItems, currentPage)
                    }
                } else {
                    return
                }
            }
        }
        return handler

    }

    override fun getDisplayName(): Component {
        return title
    }
}


class CrateInventory(crateItems: List<Prize>, currentPage: Int) : SimpleContainer(54) {
    init {
        populateInventory(crateItems, currentPage)
    }

    fun populateInventory(crateItems: List<Prize>, currentPage: Int) {
        clearContent()
        val itemsPerPage = 45
        val startIndex = currentPage * itemsPerPage
        val endIndex = (startIndex + itemsPerPage).coerceAtMost(crateItems.size)

        val totalWeight = crateItems.sumOf { it.chance }
        for (index in startIndex until endIndex) {
            val prize = crateItems[index]
            var itemStack = ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(prize.material)), prize.amount)
            val parsedName = ParseableName(prize.name).returnMessageAsStyledText()

            val chance = prize.chance.toDouble() / totalWeight.toDouble() * 100
            val roundedChance = String.format("%.2f", chance)

            if (prize.nbt != null) {
                val parsedNbt = TagParser.parseTag(prize.nbt)

                val namespacedKeyPattern = Regex("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

                val isLegacy = parsedNbt.allKeys.any { !namespacedKeyPattern.matches(it) }
                if (isLegacy) {
                    val legacyNbt = CompoundTag().apply {
                        putString("id", itemStack.itemHolder.registeredName)
                        putInt("Count", prize.amount)
                        put("tag", parsedNbt)
                    }

                    val updatedNbt = server?.fixerUpper?.update(
                        References.ITEM_STACK,
                        Dynamic(PebblesCrate.nbtOps, legacyNbt),
                        3700,
                        SharedConstants.getCurrentVersion().dataVersion.version
                    )?.value

                    itemStack = ItemStack.CODEC.parse(PebblesCrate.nbtOps, updatedNbt).result().orElse(ItemStack.EMPTY)
                } else {
                    val updatedNbt =
                        DataComponentPatch.CODEC.parse(PebblesCrate.nbtOps, TagParser.parseTag(prize.nbt)).result()
                            .orElse(null)
                    itemStack.applyComponents(updatedNbt)
                    itemStack.count = prize.amount
                }
            }

            if (prize.lore != null) {
                val lore = prize.lore
                val parsedPrizeLore = lore.map {
                    val message = it.replace("{chance}", roundedChance)
                    message.replace("{prize_name}", roundedChance)
                    ParseableMessage(message, prizeName = "placeholder").returnMessageAsStyledText()
                }
                setLore(itemStack, parsedPrizeLore)
            } else {
                setLore(itemStack, listOf(Component.literal("Chance: ${roundedChance}%")))
            }

            setItem(index - startIndex, itemStack.apply {
                set(DataComponents.CUSTOM_NAME, parsedName)
            })
        }

        // Fill the bottom row with gray stained glass
        for (i in 45..53) {
            setItem(i, ItemStack(Items.GRAY_STAINED_GLASS_PANE))
        }

        val pageText = Component.literal("Page ${currentPage + 1} of ${((crateItems.size - 1) / 45) + 1}")

        if (crateItems.size > 45) {

            // Set the page text
            setItem(52, ItemStack(Items.PAPER).apply { set(DataComponents.CUSTOM_NAME, pageText) })

            // Set the navigation arrows
            setItem(45, ItemStack(Items.ARROW).apply { set(DataComponents.CUSTOM_NAME, Component.literal("Previous")) })
            setItem(53, ItemStack(Items.ARROW).apply { set(DataComponents.CUSTOM_NAME, Component.literal("Next")) })
        }
    }
}
