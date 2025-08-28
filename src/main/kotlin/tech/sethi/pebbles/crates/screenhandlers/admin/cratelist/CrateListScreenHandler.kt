package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.screenhandlers.admin.crateconfig.CrateConfigScreenHandler
import tech.sethi.pebbles.crates.util.ParseableName

class CrateListScreenHandler(syncId: Int, player: Player) :
    ChestMenu(MenuType.GENERIC_9x6, syncId, player.inventory, SimpleContainer(9 * 6), 6) {

    private val crateConfigManager = CrateConfigManager

    init {
        val existingCrates = crateConfigManager.loadCrateConfigs()

        for ((index, crateConfig) in existingCrates.withIndex()) {
            val crateItem = ItemStack(Items.ENDER_CHEST)
            val formattedName = ParseableName(crateConfig.crateName).returnMessageAsStyledText()
            crateItem.set(DataComponents.CUSTOM_NAME, formattedName)
            container.setItem(index, crateItem)
        }

//        val createNewCrateItem = ItemStack(Items.PAPER)
//        createNewCrateItem.setCustomName(
//            Text.literal("Create New Crate").formatted(Formatting.GREEN)
//        )

        // fill last row with gray_stained_glass_pane
        for (i in 45 until 54) {
            val pane = ItemStack(Items.GRAY_STAINED_GLASS_PANE)
            pane.set(DataComponents.CUSTOM_NAME, Component.literal(""))
            container.setItem(i, pane)
        }

//        inventory.setStack(53, createNewCrateItem)
    }

    override fun stillValid(arg: Player): Boolean {
        return true
    }

    override fun clicked(slotIndex: Int, clickData: Int, actionType: ClickType, player: Player) {
        if (actionType == ClickType.THROW || actionType == ClickType.CLONE || actionType == ClickType.SWAP || actionType == ClickType.PICKUP_ALL) {
            return
        }

        player.displayClientMessage(Component.literal("Slot index: $slotIndex"), false)

        // Get material of clicked item
        player.displayClientMessage(Component.literal("Item: ${container.getItem(slotIndex)}"), false)

//        if (slotIndex == 53) { // Check if the "Create New Crate" button is clicked
//            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
//                CrateNameScreenHandler(syncId, p)
//            }, Text.of("Crate Configuration")))
//        } else
        if (slotIndex in 0..44 && actionType == ClickType.PICKUP) {
            val existingCrates = crateConfigManager.loadCrateConfigs()
            if (slotIndex in existingCrates.indices) {
                val crateConfig = existingCrates[slotIndex]
                player.displayClientMessage(Component.literal("Opening config for crate: ${crateConfig.crateName}"), false)
                // Open the configuration screen for the selected crate
                val crateName = crateConfig.crateName
                player.displayClientMessage(Component.literal("Crate name: $crateName"), false)
                player.openMenu(SimpleMenuProvider({ syncId, _, p ->
                    CrateConfigScreenHandler(syncId, p, crateName)
                }, Component.literal("$crateName Configuration")))
            }
        }
    }
}