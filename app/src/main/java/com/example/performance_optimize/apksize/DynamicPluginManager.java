package com.example.performance_optimize.apksize;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/**
 * Chapter 9: Practical Optimization of Package Size — Dynamic Resource, Class, and Native
 * Library Loading + APK Duplicate Entry Scanner.
 */
public final class DynamicPluginManager {
    private static final String TAG = "DynamicPluginManager";

    private DynamicPluginManager() {
    }

    /**
     * Chapter 9.1.2: Scans an APK archive by ZIP CRC-32 checksum to identify duplicate files.
     */
    public static List<String> scanDuplicateApkEntries(String apkPath) {
        List<String> duplicates = new ArrayList<>();
        Map<Long, String> crcMap = new HashMap<>();
        try (ZipFile zipFile = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getSize() == 0) {
                    continue;
                }
                long crc = entry.getCrc();
                String existing = crcMap.get(crc);
                if (existing != null) {
                    String report = entry.getName() + " == " + existing + " (" + entry.getSize() + "B)";
                    duplicates.add(report);
                    Log.w(TAG, "[Duplicate APK Entry] " + report);
                } else {
                    crcMap.put(crc, entry.getName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to scan APK entries", e);
        }
        return duplicates;
    }

    /**
     * Chapter 9.6.2: Dynamically constructs a Resources instance backed by an external
     * resource pack (.apk / .skin) via AssetManager.addAssetPath().
     */
    public static Resources loadExternalResources(Context context, String externalResPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(assetManager, externalResPath);

            Resources hostRes = context.getResources();
            return new Resources(assetManager, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load external resources from " + externalResPath, e);
            return null;
        }
    }

    /**
     * Chapter 9.7.2: Dynamically loads a class from an external DEX/JAR/APK file using DexClassLoader.
     */
    public static Class<?> loadExternalClass(Context context, String dexFilePath, String className) {
        try {
            File optimizedDir = context.getCodeCacheDir();
            DexClassLoader loader = new DexClassLoader(
                    dexFilePath,
                    optimizedDir.getAbsolutePath(),
                    null,
                    context.getClassLoader());
            return loader.loadClass(className);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load external class " + className, e);
            return null;
        }
    }

    /**
     * Chapter 9.8.2: Injects a custom native library directory into the host ClassLoader's
     * DexPathList.nativeLibraryDirectories list so System.loadLibrary() can resolve downloaded .so files.
     */
    @SuppressWarnings("unchecked")
    public static boolean injectNativeLibraryDir(ClassLoader classLoader, File nativeLibDir) {
        try {
            Field pathListField = findField(classLoader.getClass(), "pathList");
            Object dexPathList = pathListField.get(classLoader);
            Field nativeLibDirsField = findField(dexPathList.getClass(), "nativeLibraryDirectories");
            Object dirsObj = nativeLibDirsField.get(dexPathList);
            if (dirsObj instanceof List) {
                List<File> dirs = (List<File>) dirsObj;
                if (!dirs.contains(nativeLibDir)) {
                    dirs.add(0, nativeLibDir);
                }
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject nativeLibraryDir", e);
        }
        return false;
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> curr = clazz; curr != null; curr = curr.getSuperclass()) {
            try {
                Field field = curr.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + clazz);
    }
}
