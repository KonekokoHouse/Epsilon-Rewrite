//package com.github.epsilon.graphics.video;
//
//import com.github.epsilon.Epsilon;
//import com.mojang.logging.LogUtils;
//import net.minecraft.client.Minecraft;
//import org.apache.commons.io.IOUtils;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.Objects;
//
//public class VideoManager {
//
//    private static final File directory = new File(Minecraft.getInstance().gameDirectory, "Sakura/Background");
//    private static final File backgroundFile = new File(directory, "background.mp4");
//
//    private static void unpackFile(File file, String name) throws IOException {
//        FileOutputStream fos = new FileOutputStream(file);
//        IOUtils.copy(Objects.requireNonNull(Epsilon.class.getClassLoader().getResourceAsStream(name)), fos);
//        fos.close();
//    }
//
//    public static void ensureBackgroundFile() throws IOException {
//        if (!directory.exists()) {
//            directory.mkdirs();
//        }
//        if (!backgroundFile.exists()) {
//            unpackFile(backgroundFile, "assets/epsilon/background/wallpaper.mp4");
//        }
//    }
//
//    public static void loadBackground(int fps) throws IOException {
//        if (!backgroundFile.exists()) {
//            LogUtils.getLogger().error("Background file not found, this should not happen! Reload files.");
//            ensureBackgroundFile();
//        }
//        VideoUtil.init(backgroundFile, fps);
//    }
//
//}
