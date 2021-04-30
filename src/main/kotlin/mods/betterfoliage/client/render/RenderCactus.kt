package mods.betterfoliage.client.render

import mods.betterfoliage.BetterFoliage
import mods.betterfoliage.BetterFoliageMod
import mods.betterfoliage.client.config.Config
import mods.betterfoliage.client.render.column.ColumnTextureInfo
import mods.betterfoliage.client.render.column.SimpleColumnInfo
import mods.octarinecore.client.render.*
import mods.octarinecore.client.render.lighting.*
import mods.octarinecore.client.resource.*
import mods.octarinecore.common.Rotation
import mods.octarinecore.common.config.ModelTextureList
import mods.octarinecore.common.config.SimpleBlockMatcher
import net.minecraft.block.BlockState
import net.minecraft.block.CactusBlock
import net.minecraft.util.Direction.*
import net.minecraft.util.ResourceLocation
import java.util.concurrent.CompletableFuture

object AsyncCactusDiscovery : ConfigurableModelDiscovery<ColumnTextureInfo>() {
    override val logger = BetterFoliage.logDetail
    override val matchClasses = SimpleBlockMatcher(CactusBlock::class.java)
    override val modelTextures = listOf(ModelTextureList("block/cactus", "top", "bottom", "side"))
    override fun processModel(state: BlockState, textures: List<String>, atlas: AtlasFuture): CompletableFuture<ColumnTextureInfo>? {
        val sprites = textures.map { atlas.sprite(ResourceLocation(it)) }
        return atlas.mapAfter {
            SimpleColumnInfo(
                Axis.Y,
                sprites[0].get(),
                sprites[1].get(),
                sprites.drop(2).map { it.get() }
            )
        }
    }

    fun init() {
        BetterFoliage.blockSprites.providers.add(this)
    }
}

class RenderCactus : RenderDecorator(BetterFoliageMod.MOD_ID, BetterFoliageMod.bus) {

    val cactusStemRadius = 0.4375
    val cactusArmRotation = listOf(NORTH, SOUTH, EAST, WEST).map { Rotation.rot90[it.ordinal] }

    val iconCross by sprite(ResourceLocation(BetterFoliageMod.MOD_ID, "blocks/better_cactus"))
    val iconArm = spriteSet { idx -> ResourceLocation(BetterFoliageMod.MOD_ID, "blocks/better_cactus_arm_$idx") }

    val modelStem = model {
        horizontalRectangle(x1 = -cactusStemRadius, x2 = cactusStemRadius, z1 = -cactusStemRadius, z2 = cactusStemRadius, y = 0.5)
        .scaleUV(cactusStemRadius * 2.0)
        .let { listOf(it.flipped.move(1.0 to DOWN), it) }
        .forEach { it.setAoShader(faceOrientedAuto(corner = cornerAo(Axis.Y), edge = null)).add() }

        verticalRectangle(x1 = -0.5, z1 = cactusStemRadius, x2 = 0.5, z2 = cactusStemRadius, yBottom = -0.5, yTop = 0.5)
        .setAoShader(faceOrientedAuto(corner = cornerAo(Axis.Y), edge = null))
        .toCross(UP).addAll()
    }
    val modelCross = modelSet(64) { modelIdx ->
        verticalRectangle(x1 = -0.5, z1 = 0.5, x2 = 0.5, z2 = -0.5, yBottom = -0.5 * 1.41, yTop = 0.5 * 1.41)
        .setAoShader(edgeOrientedAuto(corner = cornerAoMaxGreen))
        .scale(1.4)
        .transformV { v ->
            val perturb = xzDisk(modelIdx) * Config.cactus.sizeVariation
            Vertex(v.xyz + (if (v.uv.u < 0.0) perturb else -perturb), v.uv, v.aoShader)
        }
        .toCross(UP).addAll()
    }
    val modelArm = modelSet(64) { modelIdx ->
        verticalRectangle(x1 = -0.5, z1 = 0.5, x2 = 0.5, z2 = -0.5, yBottom = 0.0, yTop = 1.0)
        .scale(Config.cactus.size).move(0.5 to UP)

        .setAoShader(faceOrientedAuto(overrideFace = UP, corner = cornerAo(Axis.Y), edge = null))
        .toCross(UP) { it.move(xzDisk(modelIdx) * Config.cactus.hOffset) }.addAll()
    }

    override fun isEligible(ctx: CombinedContext): Boolean =
        Config.enabled && Config.cactus.enabled &&
        AsyncCactusDiscovery[ctx] != null

    override val onlyOnCutout get() = true

    override fun render(ctx: CombinedContext) {
        val icons = AsyncCactusDiscovery[ctx]!!

        ctx.render(
            modelStem.model,
            icon = { ctx, qi, q -> when(qi) {
                0 -> icons.bottom(ctx, qi, q); 1 -> icons.top(ctx, qi, q); else -> icons.side(ctx, qi, q)
            } }
        )
        ctx.render(
            modelCross[ctx.semiRandom(0)],
            icon = { _, _, _ -> iconCross }
        )

        ctx.render(
            modelArm[ctx.semiRandom(1)],
            cactusArmRotation[ctx.semiRandom(2) % 4],
            icon = { _, _, _ -> iconArm[ctx.semiRandom(3)] }
        )
    }
}