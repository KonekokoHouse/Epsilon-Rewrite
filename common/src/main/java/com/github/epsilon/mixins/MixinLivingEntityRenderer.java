package com.github.epsilon.mixins;

import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.impl.render.Chams;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static com.github.epsilon.Constants.mc;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<S extends LivingEntityRenderState> {

    @Shadow
    public abstract Identifier getTextureLocation(S s);

    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
    private RenderType modifyRenderType(RenderType original, S state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        Chams chamsModule = Chams.INSTANCE;

        if (!chamsModule.isEnabled() || state.entityType != EntityTypes.PLAYER) {
            return original;
        }

        return Chams.INSTANCE.getRenderType(getTextureLocation(state));
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"))
    private float hookHeadYaw(float original, LivingEntity entity, S state, float partialTick) {
        if (entity == mc.player) {
            Vector2f rotation = RotationManager.INSTANCE.animationRotation;
            Vector2f lastRotation = RotationManager.INSTANCE.lastAnimationRotation;
            if (rotation != null && lastRotation != null && RotationManager.INSTANCE.isActive()) {
                float lastYaw = lastRotation.x;
                float currentYaw = rotation.x;
                float diff = Mth.wrapDegrees(currentYaw - lastYaw);
                return Mth.wrapDegrees(lastYaw + diff * partialTick);
            }
        }
        return original;
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"))
    private float hookPitch(float original, LivingEntity entity, S state, float partialTick) {
        if (entity == mc.player) {
            Vector2f rotation = RotationManager.INSTANCE.animationRotation;
            Vector2f lastRotation = RotationManager.INSTANCE.lastAnimationRotation;
            if (rotation != null && lastRotation != null && RotationManager.INSTANCE.isActive()) {
                float lastPitch = lastRotation.y;
                float currentPitch = rotation.y;
                float diff = currentPitch - lastPitch;
                return lastPitch + diff * partialTick;
            }
        }
        return original;
    }

}
