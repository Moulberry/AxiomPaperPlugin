package com.moulberry.axiom.paperapi.display;

import com.moulberry.axiom.paperapi.block.AxiomPlacementLogic;
import com.moulberry.axiom.paperapi.block.AxiomProperty;
import com.moulberry.axiom.paperapi.block.ImplAxiomCustomBlock;
import net.kyori.adventure.key.Key;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AxiomCustomDisplayBuilder {
    private final Key key;
    private final String searchKey;
    private final ItemStack itemStack;
    private @Nullable Vector3f defaultTranslation = null;
    private @Nullable Quaternionf defaultLeftRotation = null;
    private @Nullable Vector3f defaultScale = null;
    private @Nullable Quaternionf defaultRightRotation = null;
    private int defaultBlockBrightnessOverride = -1;
    private int defaultSkyBrightnessOverride = -1;

    public AxiomCustomDisplayBuilder(Key key, String searchKey, ItemStack itemStack) {
        this.key = key;
        this.searchKey = searchKey;
        this.itemStack = itemStack;
    }

    ImplAxiomCustomDisplay build() {
        return new ImplAxiomCustomDisplay(this.key, this.searchKey, this.itemStack,
            this.defaultTranslation, this.defaultLeftRotation, this.defaultScale, this.defaultRightRotation,
            this.defaultBlockBrightnessOverride, this.defaultSkyBrightnessOverride);
    }

    public void setDefaultTranslation(float x, float y, float z) {
        this.defaultTranslation = new Vector3f(x, y, z);
    }

    public void setDefaultLeftRotation(float x, float y, float z, float w) {
        this.defaultLeftRotation = new Quaternionf(x, y, z, w);
    }

    public void setDefaultScale(float x, float y, float z) {
        this.defaultScale = new Vector3f(x, y, z);
    }

    public void setDefaultRightRotation(float x, float y, float z, float w) {
        this.defaultRightRotation = new Quaternionf(x, y, z, w);
    }

    public void setDefaultBrightness(int block, int sky) {
        this.defaultBlockBrightnessOverride = Math.max(0, Math.min(15, block));
        this.defaultSkyBrightnessOverride = Math.max(0, Math.min(15, sky));
    }

}
