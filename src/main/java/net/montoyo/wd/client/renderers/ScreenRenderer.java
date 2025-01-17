/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.montoyo.mcef.api.IStringVisitor;
import net.montoyo.wd.SharedProxy;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.entity.ServerEventHandler;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.miniserv.SyncPlugin;
import net.montoyo.wd.utilities.Vector3f;
import net.montoyo.wd.utilities.Vector3i;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.mojang.math.Vector3f.*;
import static org.lwjgl.opengl.GL11.*;

public class ScreenRenderer implements BlockEntityRenderer<TileEntityScreen> {

    private String url;

    public ScreenRenderer(String url) {
        this.url = url;
    }

    public ScreenRenderer() {}

    public static class ScreenRendererProvider implements BlockEntityRendererProvider<TileEntityScreen> {
        @Override
        public @NotNull BlockEntityRenderer<TileEntityScreen> create(@NotNull Context arg) {
            return new ScreenRenderer();
        }
    }

    private final Vector3f mid = new Vector3f();
    private final Vector3i tmpi = new Vector3i();
    private final Vector3f tmpf = new Vector3f();

    @Override
    public void render(TileEntityScreen te, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if(!te.isLoaded())
            return;

        //Disable lighting
        RenderSystem.enableTexture();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();

        for(int i = 0; i < te.screenCount(); i++) {
            TileEntityScreen.Screen scr = te.getScreen(i);
            if(scr.browser == null) {
                if(WebDisplays.PROXY instanceof ClientProxy clientProxy) {
                    scr.browser = clientProxy.getMCEF().createBrowser(WebDisplays.applyBlacklist(this.url != null ? this.url : "https://www.google.com"));

                    if (scr.rotation.isVertical)
                        scr.browser.resize(scr.resolution.y, scr.resolution.x);
                    else
                        scr.browser.resize(scr.resolution.x, scr.resolution.y);

                    scr.doTurnOnAnim = true;
                    scr.turnOnTime = System.currentTimeMillis();
                }
            }

            tmpi.set(scr.side.right);
            tmpi.mul(scr.size.x);
            tmpi.addMul(scr.side.up, scr.size.y);
            tmpf.set(tmpi);
            mid.set(0.5, 0.5, 0.5);
            mid.addMul(tmpf, 0.5f);
            tmpf.set(scr.side.left);
            mid.addMul(tmpf, 0.5f);
            tmpf.set(scr.side.down);
            mid.addMul(tmpf, 0.5f);

            poseStack.pushPose();
            poseStack.translate(mid.x, mid.y, mid.z);

            switch(scr.side) {
                case BOTTOM:
                    poseStack.mulPose(XP.rotation(90.f + 49.8f));
                    break;

                case TOP:
                    poseStack.mulPose(XN.rotation(90.f + 49.8f));
                    break;

                case NORTH:
                    poseStack.mulPose(YN.rotationDegrees(180.f));
                    break;

                case SOUTH:
                    break;

                case WEST:
                    poseStack.mulPose(YN.rotationDegrees(90.f));
                    break;

                case EAST:
                    poseStack.mulPose(YP.rotationDegrees(90.f));
                    break;
            }

            if(scr.doTurnOnAnim) {
                long lt = System.currentTimeMillis() - scr.turnOnTime;
                float ft = ((float) lt) / 100.0f;

                if(ft >= 1.0f) {
                    ft = 1.0f;
                    scr.doTurnOnAnim = false;
                }

                poseStack.scale(ft, ft, 1.0f);
            }

            if(!scr.rotation.isNull)
                poseStack.mulPose(ZP.rotationDegrees(scr.rotation.angle));

            float sw = ((float) scr.size.x) * 0.5f - 2.f / 16.f;
            float sh = ((float) scr.size.y) * 0.5f - 2.f / 16.f;

            if(scr.rotation.isVertical) {
                float tmp = sw;
                sw = sh;
                sh = tmp;
            }

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            //TODO: Use tesselator
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem._setShaderTexture(0, scr.browser.getTextureID());
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            builder.vertex(poseStack.last().pose(),-sw, -sh, 0.505f).uv(0.f, 1.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
            builder.vertex(poseStack.last().pose(), sw, -sh, 0.505f).uv(1.f, 1.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
            builder.vertex(poseStack.last().pose(), sw,  sh, 0.505f).uv(1.f, 0.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
            builder.vertex(poseStack.last().pose(),-sw,  sh, 0.505f).uv(0.f, 0.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
            tesselator.end();//Minecraft does shit with mah texture otherwise...
            RenderSystem.disableDepthTest();
            poseStack.popPose();
        }


        //Bounding box debugging
        poseStack.pushPose();
        poseStack.translate(-te.getBlockPos().getX(), -te.getBlockPos().getY(), -te.getBlockPos().getZ());
        LevelRenderer.renderLineBox(
                poseStack, bufferSource.getBuffer(RenderType.LINES),
                te.getRenderBoundingBox(), 1, 1, 1, 1f
        );
        poseStack.popPose();

        //Re-enable lighting
        RenderSystem.enableCull();
    }
}
