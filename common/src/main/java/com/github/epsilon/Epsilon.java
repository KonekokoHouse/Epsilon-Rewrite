package com.github.epsilon;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.managers.AddonManager;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.managers.HealthManager;
import com.github.epsilon.managers.ModuleManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

public class Epsilon {

    public static final String MOD_ID = BuildConfig.MOD_ID;
    public static final String VERSION = BuildConfig.VERSION;

    public static final Logger LOGGER = LogManager.getLogger("Epsilon");

    public static int skipTicks;

    public static void init() {
        LOGGER.info("Welcome to Epsilon, Meow~");

        EventBus.INSTANCE.registerLambdaFactory(Epsilon.class.getPackageName(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        ModuleManager.INSTANCE.initModules();
        AddonManager.INSTANCE.setupAddons();
        ConfigManager.INSTANCE.initConfig();
        HealthManager.INSTANCE.getClass();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.INSTANCE.saveNow();
            Epsilon.LOGGER.info("Epsilon saved config on shutdown");
        }));

        Epsilon.LOGGER.info("Epsilon has loaded successfully, Meow~");
    }

}
