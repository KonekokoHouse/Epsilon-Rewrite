package com.github.epsilon.fabric.compat;

import com.github.epsilon.PlatformCompat;
import com.github.epsilon.compat.EquipmentSlotCompat;
import com.github.epsilon.compat.KeyMappingCompat;
import com.github.epsilon.compat.VertexFormatCompat;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric platform facade composed from concern-specific compat components.
 */
public class FabricPlatformCompat implements PlatformCompat {

    private final KeyMappingCompat keyMappingCompat;
    private final EquipmentSlotCompat equipmentSlotCompat;
    private final VertexFormatCompat vertexFormatCompat;

    public FabricPlatformCompat() {
        this(new FabricKeyMappingCompat(), new FabricEquipmentSlotCompat(), new FabricVertexFormatCompat());
    }

    public FabricPlatformCompat(
            KeyMappingCompat keyMappingCompat,
            EquipmentSlotCompat equipmentSlotCompat,
            VertexFormatCompat vertexFormatCompat
    ) {
        this.keyMappingCompat = keyMappingCompat;
        this.equipmentSlotCompat = equipmentSlotCompat;
        this.vertexFormatCompat = vertexFormatCompat;
    }

    @Override
    public InputConstants.Key getKeyMappingKey(KeyMapping keyMapping) {
        return keyMappingCompat.getKeyMappingKey(keyMapping);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return equipmentSlotCompat.getEquipmentSlot(stack);
    }

    @Override
    public int findNextVertexFormatElementId() {
        return vertexFormatCompat.findNextVertexFormatElementId();
    }

    @Override
    public VertexFormatElement registerVertexFormatElement(int id, int index, VertexFormatElement.Type type, boolean normalized, int count) {
        return vertexFormatCompat.registerVertexFormatElement(id, index, type, normalized, count);
    }

}
