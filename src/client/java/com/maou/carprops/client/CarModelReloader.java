package com.maou.carprops.client;

import com.maou.carprops.CarProps;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class CarModelReloader implements SimpleSynchronousResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return CarProps.id("car_models");
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        CarModelManager.reload(manager);
    }
}
