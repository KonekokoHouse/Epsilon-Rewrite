//package com.github.epsilon.graphics.video;
//
//import com.github.epsilon.Epsilon;
//import com.github.epsilon.assets.resources.ResourceLocationUtils;
//import com.mojang.blaze3d.platform.NativeImage;
//import com.mojang.blaze3d.systems.CommandEncoder;
//import com.mojang.blaze3d.systems.RenderSystem;
//import com.mojang.blaze3d.textures.GpuTextureView;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.renderer.texture.DynamicTexture;
//import net.minecraft.resources.Identifier;
//import org.bytedeco.javacv.FFmpegFrameGrabber;
//import org.bytedeco.javacv.Frame;
//
//import java.io.File;
//import java.nio.ByteBuffer;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.concurrent.locks.LockSupport;
//
//import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
//
//public class VideoUtil {
//
//    private static final Minecraft mc = Minecraft.getInstance();
//
//    private static final VideoGpuTexture VIDEO_TEX = new VideoGpuTexture();
//
//    private static final Identifier GUI_TEXTURE_ID = ResourceLocationUtils.getIdentifier("dynamic/title_video");
//
//    private static DynamicTexture registeredTexture;
//
//    private static FFmpegFrameGrabber grabber;
//
//    private static double frameRate;
//
//    private static long mediaStartUs;
//    private static long wallStartNs;
//
//
//    private static volatile long lastPtsUs = 0;
//
//    private static ExecutorService executor;
//    private static Future<?> decodeFuture;
//
//    private static final Object pauseLock = new Object();
//
//    private static volatile boolean paused = false;
//    private static volatile boolean stopped = true;
//
//    private static final Object GRABBER_LOCK = new Object();
//
//    private static final AtomicReference<FrameBuffer> latestFrame = new AtomicReference<>();
//
//    private static final int POOL = 4;
//    private static final ByteBuffer[] pool = new ByteBuffer[POOL];
//    private static int poolCursor = 0;
//
//    private static void releaseGuiTexture() {
//        if (registeredTexture == null) {
//            return;
//        }
//        mc.getTextureManager().release(GUI_TEXTURE_ID);
//        registeredTexture = null;
//    }
//
//    private static ByteBuffer acquire(int size) {
//        FrameBuffer cur = latestFrame.get();
//        ByteBuffer curBuf = cur != null ? cur.buffer : null;
//
//        for (int n = 0; n < POOL; n++) {
//            int idx = (poolCursor + n) % POOL;
//            ByteBuffer b = pool[idx];
//
//            if (b == curBuf) continue;
//            if (b == null || b.capacity() < size) {
//                b = ByteBuffer.allocateDirect(size);
//                pool[idx] = b;
//            }
//
//            poolCursor = (idx + 1) % POOL;
//            return b;
//        }
//
//        return ByteBuffer.allocateDirect(size);
//    }
//
//    private static final class VideoGpuTexture {
//        private DynamicTexture tex;
//        private int w = -1, h = -1;
//
//        void close() {
//            if (tex != null) {
//                if (registeredTexture == tex) {
//                    releaseGuiTexture();
//                } else {
//                    try {
//                        tex.close();
//                    } catch (Throwable ignored) {
//                    }
//                }
//                tex = null;
//            }
//            w = h = -1;
//        }
//
//        GpuTextureView view() {
//            return tex != null ? tex.getTextureView() : null;
//        }
//
//        DynamicTexture texture() {
//            return tex;
//        }
//
//        int width() {
//            return w;
//        }
//
//        int height() {
//            return h;
//        }
//
//        void ensureSize(int newW, int newH) {
//            if (tex != null && w == newW && h == newH) return;
//
//            close();
//            tex = new DynamicTexture("video", newW, newH, true);
//            w = newW;
//            h = newH;
//        }
//
//        void uploadRgba(ByteBuffer rgba, int frameW, int frameH) {
//            ensureSize(frameW, frameH);
//
//            ByteBuffer src = rgba.duplicate();
//            src.rewind();
//
//            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
//            enc.writeToTexture(
//                    tex.getTexture(),
//                    src,
//                    NativeImage.Format.RGBA,
//                    0,
//                    0,
//                    0,
//                    0,
//                    frameW,
//                    frameH
//            );
//        }
//    }
//
//    public static void init(File file, int fps) {
//        stop();
//
//        try {
//            Epsilon.LOGGER.info("[VideoPlayer] Initializing... {}", file.getAbsolutePath());
//
//            executor = Executors.newSingleThreadExecutor(r -> {
//                Thread t = new Thread(r, "VideoUtil-Decode");
//                t.setDaemon(true);
//                return t;
//            });
//
//            synchronized (GRABBER_LOCK) {
//                grabber = new FFmpegFrameGrabber(file);
//                grabber.setPixelFormat(AV_PIX_FMT_RGBA);
//                grabber.setOption("threads", "4");
//                grabber.start();
//
//                frameRate = (fps == -1 ? grabber.getFrameRate() : fps);
//
//                Frame first = grabber.grabImage();
//                if (first != null && first.image != null) {
//                    FrameBuffer fb = copyFrameLocked(first);
//                    latestFrame.set(fb);
//                    lastPtsUs = (first.timestamp > 0 ? first.timestamp : grabber.getTimestamp());
//                }
//
//                mediaStartUs = grabber.getTimestamp();
//                wallStartNs = System.nanoTime();
//                lastPtsUs = mediaStartUs;
//            }
//
//            stopped = false;
//            paused = false;
//
//            startDecodeThread();
//        } catch (Throwable e) {
//            Epsilon.LOGGER.error("[VideoPlayer] Init error:", e);
//            stop();
//        }
//    }
//
//    public static void pause() {
//        paused = true;
//    }
//
//    public static void resume() {
//        if (!paused) return;
//
//        synchronized (pauseLock) {
//            paused = false;
//            wallStartNs = System.nanoTime() - (lastPtsUs - mediaStartUs) * 1000L;
//            pauseLock.notifyAll();
//        }
//    }
//
//    public static void stop() {
//        try {
//            if (stopped && grabber == null) return;
//
//            stopped = true;
//
//            synchronized (pauseLock) {
//                paused = false;
//                pauseLock.notifyAll();
//            }
//
//            if (decodeFuture != null) {
//                decodeFuture.cancel(true);
//                decodeFuture = null;
//            }
//
//            if (executor != null) {
//                executor.shutdown();
//                executor.awaitTermination(2, TimeUnit.SECONDS);
//                executor = null;
//            }
//
//            synchronized (GRABBER_LOCK) {
//                if (grabber != null) {
//                    try {
//                        grabber.stop();
//                    } catch (Throwable ignored) {
//                    }
//                    try {
//                        grabber.close();
//                    } catch (Throwable ignored) {
//                    }
//                    grabber = null;
//                }
//            }
//
//            latestFrame.set(null);
//
//
//            Runnable closeGpu = VIDEO_TEX::close;
//            if (RenderSystem.isOnRenderThread()) {
//                closeGpu.run();
//            } else {
//                RenderSystem.queueFencedTask(closeGpu);
//            }
//
//            Epsilon.LOGGER.info("[VideoPlayer] Stopped");
//        } catch (Throwable e) {
//            Epsilon.LOGGER.error("[VideoPlayer] Stop error:", e);
//        }
//    }
//
//    public static Identifier getGuiTexture() {
//        if (stopped || paused) return null;
//        if (!RenderSystem.isOnRenderThread()) return null;
//
//        FrameBuffer frame = latestFrame.get();
//        if (frame == null) return null;
//
//        VIDEO_TEX.uploadRgba(frame.buffer, frame.width, frame.height);
//
//        DynamicTexture texture = VIDEO_TEX.texture();
//        if (texture == null) return null;
//
//        if (registeredTexture != texture) {
//            mc.getTextureManager().register(GUI_TEXTURE_ID, texture);
//            registeredTexture = texture;
//        }
//
//        return GUI_TEXTURE_ID;
//    }
//
//    public static int getGuiTextureWidth() {
//        int width = VIDEO_TEX.width();
//        if (width > 0) {
//            return width;
//        }
//        FrameBuffer frame = latestFrame.get();
//        return frame != null ? frame.width : -1;
//    }
//
//    public static int getGuiTextureHeight() {
//        int height = VIDEO_TEX.height();
//        if (height > 0) {
//            return height;
//        }
//        FrameBuffer frame = latestFrame.get();
//        return frame != null ? frame.height : -1;
//    }
//
//    private static void startDecodeThread() {
//        decodeFuture = executor.submit(() -> {
//            try {
//                while (!stopped) {
//                    if (paused) {
//                        synchronized (pauseLock) {
//                            while (paused && !stopped) pauseLock.wait();
//                        }
//                        if (stopped) break;
//                    }
//
//                    Frame frame;
//                    long ptsUs;
//
//                    synchronized (GRABBER_LOCK) {
//                        if (stopped || grabber == null) break;
//
//                        frame = grabber.grabImage();
//                        if (frame == null || frame.image == null) {
//                            grabber.setTimestamp(0);
//                            Frame first = grabber.grabImage();
//                            if (first != null && first.image != null) {
//                                ptsUs = (first.timestamp > 0 ? first.timestamp : grabber.getTimestamp());
//                                lastPtsUs = ptsUs;
//
//                                latestFrame.set(copyFrameLocked(first));
//                                mediaStartUs = grabber.getTimestamp();
//                                wallStartNs = System.nanoTime();
//                            }
//                            continue;
//                        }
//
//                        ptsUs = (frame.timestamp > 0 ? frame.timestamp : grabber.getTimestamp());
//                        lastPtsUs = ptsUs;
//
//                        latestFrame.set(copyFrameLocked(frame));
//                    }
//
//                    long targetNs = wallStartNs + (ptsUs - mediaStartUs) * 1000L;
//                    long sleepNs = targetNs - System.nanoTime();
//                    if (sleepNs > 0) {
//                        LockSupport.parkNanos(Math.min(sleepNs, 5_000_000L));
//                    }
//                }
//            } catch (InterruptedException ignored) {
//            } catch (Throwable e) {
//                Epsilon.LOGGER.error("[VideoPlayer] Decode thread error:", e);
//            }
//        });
//    }
//
//    private static FrameBuffer copyFrameLocked(Frame frame) {
//        int w = frame.imageWidth;
//        int h = frame.imageHeight;
//
//        int channels = 4;
//        int size = w * h * channels;
//
//        ByteBuffer src = (ByteBuffer) frame.image[0];
//
//        ByteBuffer s = src.duplicate();
//        s.rewind();
//
//
//        if (s.remaining() < size) {
//            size = s.remaining();
//        } else if (s.remaining() > size) {
//            s.limit(size);
//        }
//
//        ByteBuffer dst = acquire(size);
//        dst.clear();
//        dst.put(s);
//        dst.flip();
//
//        return new FrameBuffer(dst, w, h);
//    }
//
//    private record FrameBuffer(ByteBuffer buffer, int width, int height) {
//    }
//
//}
