package tech.sethi.pebbles.crates.screenhandlers

import ca.landonjw.gooeylibs2.api.UIManager
import ca.landonjw.gooeylibs2.api.button.GooeyButton
import ca.landonjw.gooeylibs2.api.button.PlaceholderButton
import ca.landonjw.gooeylibs2.api.button.linked.LinkType
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper
import ca.landonjw.gooeylibs2.api.page.LinkedPage
import ca.landonjw.gooeylibs2.api.template.LineType
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import tech.sethi.pebbles.crates.lootcrates.CrateConfig

/**
 * Conversion of the original chest-menu-based prize preview to GooeyLibs2.
 *
 * - 9x6 chest view (54 slots).
 * - Prize grid: indices [0, 44] (5 rows * 9 = 45 items/page).
 * - Bottom row [45, 53]: UI chrome
 *     45 = Prev, 52 = Page indicator, 53 = Next, others = Gray panes.
 *
 * Usage:
 *   PrizeDisplayGooey(title, crateConfig).open(player)
 */
object PrizeDisplay {
    private var PREVIOUS = LinkedPageButton.builder().display(Items.ARROW.defaultInstance).with(DataComponents.CUSTOM_NAME, Component.literal("Previous")).linkType(LinkType.Previous).build()
    private var NEXT = LinkedPageButton.builder().display(Items.ARROW.defaultInstance).with(DataComponents.CUSTOM_NAME, Component.literal("Next")).linkType(LinkType.Next).build()
    private var BLANK = GooeyButton.builder().display(Items.GRAY_STAINED_GLASS_PANE.defaultInstance).with(DataComponents.CUSTOM_NAME, Component.literal("")).build()
    private var PLACEHOLDER = PlaceholderButton()

    private var TEMPLATE = ChestTemplate.builder(6).fill(PLACEHOLDER)
        .line(LineType.HORIZONTAL, 5, 0, 9, BLANK)
        .set(5, 1, PREVIOUS)
        .set(5, 7, NEXT)
        .build()

    fun open(title: Component, crateConfig: CrateConfig, player: Player) {
        var totalWeight = crateConfig.prize.sumOf { it.chance }
        var list = crateConfig.prize.map { it.toStack(totalWeight) }.map(GooeyButton::of).toList()

        var page = PaginationHelper.createPagesFromPlaceholders(TEMPLATE, list, LinkedPage.builder().title(Component.empty().append(title).append("(" + LinkedPage.CURRENT_PAGE_PLACEHOLDER + "/" + LinkedPage.TOTAL_PAGES_PLACEHOLDER + ")")))

        UIManager.openUIForcefully(player as ServerPlayer, page)
    }

}
