package mods.betterfoliage.client.render

import mods.betterfoliage.BetterFoliageMod
import mods.betterfoliage.client.config.Config
import mods.octarinecore.client.render.CombinedContext
import mods.octarinecore.client.render.RenderDecorator
import mods.octarinecore.client.render.lighting.cornerAo
import mods.octarinecore.client.render.lighting.cornerFlat
import mods.octarinecore.client.render.lighting.faceOrientedAuto
import mods.octarinecore.random
import net.minecraft.block.Blocks
import net.minecraft.util.Direction.Axis
import net.minecraft.util.Direction.DOWN
import net.minecraft.util.Direction.UP
import net.minecraft.util.ResourceLocation

class RenderNetherrack : RenderDecorator(BetterFoliageMod.MOD_ID, BetterFoliageMod.bus) {

    val netherrackIcon = spriteSet { idx -> ResourceLocation(BetterFoliageMod.MOD_ID, "blocks/better_netherrack_$idx") }
    val netherrackModel = modelSet(64) { modelIdx ->
        verticalRectangle(x1 = -0.5, z1 = 0.5, x2 = 0.5, z2 = -0.5, yTop = -0.5,
        yBottom = -0.5 - random(Config.netherrack.heightMin, Config.netherrack.heightMax))
        .setAoShader(faceOrientedAuto(overrideFace = DOWN, corner = cornerAo(Axis.Y)))
        .setFlatShader(faceOrientedAuto(overrideFace = DOWN, corner = cornerFlat))
        .toCross(UP) { it.move(xzDisk(modelIdx) * Config.shortGrass.hOffset) }.addAll()

    }

    override fun isEligible(ctx: CombinedContext) =
        Config.enabled && Config.netherrack.enabled && ctx.state.block == Blocks.NETHERRACK

    override fun render(ctx: CombinedContext) {
        ctx.render()
        if (!ctx.isCutout) return
        if (ctx.offset(DOWN).isNormalCube) return

        val rand = ctx.semiRandomArray(2)
        ctx.render(
            netherrackModel[rand[0]],
            icon = { _, qi, _ -> netherrackIcon[rand[qi and 1]] }
        )
    }
}