package com.github.epsilon.mixins.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanDevice.class)
public class MixinVulkanDevice {

    @Inject(method = "close", at = @At("HEAD"))
    public void onClose(CallbackInfo ci) {
        LuminRenderSystem.destroyVulkanContext();
    }

}
