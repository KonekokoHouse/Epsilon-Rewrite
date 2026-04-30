package com.github.epsilon;

import com.github.epsilon.assets.i18n.I18NFileGenerator;
import com.github.epsilon.managers.AddonManager;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.managers.SyncManager;
import com.github.epsilon.managers.TargetManager;
import com.github.epsilon.managers.network.ClientboundPacketManager;
import com.github.epsilon.managers.network.ServerboundPacketManager;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common initialization logic shared by all loaders.
 */
public class Epsilon {

    public static final String MODID = "open_epsilon";
    public static String VERSION = "Loading ...";

    public static final Logger LOGGER = LogManager.getLogger("Open Epsilon");

    public static int skipTicks;
    public static Minecraft mc;

    /**
     * Active platform implementation. Must be set by the loader entry point
     * <em>before</em> calling {@link #init()}.
     */
    public static PlatformCompat platform;

    /**
     * Called during client setup on all loaders.
     */
    public static void init() {
        if (platform == null) {
            throw new IllegalStateException("Epsilon.platform must be set before calling Epsilon.init()");
        }
        LOGGER.info("Welcome to Epsilon, Meow~");

        mc = Minecraft.getInstance();

        // 初始化 Managers
        ModuleManager.INSTANCE.initModules();
        AddonManager.INSTANCE.setupAddons();

        SyncManager.INSTANCE.getClass();
        ClientboundPacketManager.INSTANCE.getClass();
        ServerboundPacketManager.INSTANCE.getClass();

        TargetManager.INSTANCE.clearSharedTarget();
        ConfigManager.INSTANCE.initConfig();

        // 生成空的 i18n 文件
        I18NFileGenerator.generate("epsilon-config/empty-i18n.json");

        // 添加一个退出游戏时候的钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.INSTANCE.saveNow();
            LOGGER.info("お兄ちゃん、私はあなたを一番愛しています~");
        }));

        LOGGER.info("Epsilon has loaded successfully, Meow~");
    }

}

