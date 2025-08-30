package tech.sethi.pebbles.crates.screenhandlers.admin

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.Prize
import tech.sethi.pebbles.crates.screenhandlers.GenericChestMenu
import java.math.BigDecimal

class PreviewIconScreenHandler(
    syncId: Int, player: Player, private val crateName: String
) : GenericChestMenu(MenuType.GENERIC_9x3, syncId, player.inventory, SimpleContainer(9 * 3), 3) {

    private val oddsSumItem = ItemStack(Items.PAPER)
    private val crateConfigManager = CrateConfigManager

    init {
        val existingCrates = crateConfigManager.loadCrateConfigs()
        val currentCrateConfig = existingCrates.first { it.crateName == crateName }

        val inventory = container

        val crateItems = currentCrateConfig.prize

        for (i in 0 until inventory.containerSize) {
            inventory.setItem(i, ItemStack.EMPTY)
        }

        for ((index, prize) in crateItems.withIndex()) {
            val materialIdentifier = ResourceLocation.tryParse(prize.material)
            if (materialIdentifier != null) {
                val item = BuiltInRegistries.ITEM.get(materialIdentifier)
                if (item != Items.AIR) {
                    val itemStack = ItemStack(item, prize.amount)
                    val nbt = CompoundTag().apply {
                        putString("PebblesCrateNBT", prize.nbt ?: "")
                    }
                    itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
                    setLore(itemStack, prize.commands.map { Component.literal(it) })
                    inventory.setItem(index, itemStack)
                }
            }
        }
    }

    override fun stillValid(arg: Player): Boolean = true

    override fun clicked(slotIndex: Int, clickData: Int, actionType: ClickType, player: Player) {
        // Open the IndividualRewardEditingScreen for the clicked item
        // Save the preview items and their weights to a JSON file
        if (slotIndex == 9 * 3 - 1) {
            // Save the preview items
            player.displayClientMessage(Component.literal("Saving..."), false)
            // Get the current crate configurations
            val currentCrateConfigs = crateConfigManager.loadCrateConfigs()

            // Update or add the crate configuration
            val updatedPrizes = ArrayList<Prize>()
            for (i in 0 until 18) {
                val stack = container.getItem(i)
                if (!stack.isEmpty) {
                    getWeightFromLore(stack).let { weight ->
                        val prize = currentCrateConfigs.first { it.crateName == crateName }.prize[i]
                        updatedPrizes.add(prize.copy(chance = weight.toInt()))
                    }
                }
            }
            val existingCrateConfig = currentCrateConfigs.find { it.crateName == crateName }
            if (existingCrateConfig != null) {
                existingCrateConfig.prize = updatedPrizes
            } else {
                val currentCrateConfig = currentCrateConfigs.first { it.crateName == crateName }
                val newCrateConfig = CrateConfig(
                    crateName = crateName, crateKey = currentCrateConfig.crateKey, prize = updatedPrizes
                )
                crateConfigManager.setCrateConfig(crateName, newCrateConfig)
            }

            // Save the updated crate configurations
            crateConfigManager.saveCrateConfigs(currentCrateConfigs)

            player.displayClientMessage(Component.literal("Saved!"), false)
            player.openMenu(SimpleMenuProvider({ syncId, _, p ->
                IndividualCrateConfigScreenHandler(syncId, p, crateName)
            }, Component.literal("$crateName Config")))

        } else {
            super.clicked(slotIndex, clickData, actionType, player)
        }
    }


    private fun setLore(itemStack: ItemStack, lore: List<Component>) {
        val loreComponent = ItemLore(lore)
        itemStack.set(DataComponents.LORE, loreComponent)
    }


    private fun getWeightFromLore(itemStack: ItemStack): BigDecimal {
        val lore = itemStack.get(DataComponents.LORE)?.lines
        val line = Component.Serializer.fromJson(lore?.get(0)?.string, RegistryAccess.EMPTY)
        return if (lore != null && lore.size > 0) BigDecimal(line?.string?.split(": ")?.get(1) ?: "0")
        else BigDecimal.ZERO
    }
}