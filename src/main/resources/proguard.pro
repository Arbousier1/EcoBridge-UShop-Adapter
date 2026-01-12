# --- 基础通用设置 ---
-dontshrink
-dontoptimize
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-dontwarn **

# --- 保护插件主类 ---
-keep public class top.ellan.middleware.EcoBridgeMiddleware { *; }

# --- 保护事件监听器 ---
-keepclassmembers class * {
    @org.bukkit.event.EventHandler *;
}

# --- 保护经济钩子 ---
-keep public class top.ellan.middleware.hook.UShopEconomyHook { *; }

# --- 保护核心拦截器与索引机制 (关键修复) ---
-keepclassmembers class top.ellan.middleware.listener.ShopInterceptor {
    public static void init(...);
    public static void buildIndex();
    public static void clearAllCaches();
    public static int getItemSignature(...);
    public static double safeCalculatePrice(...);
    # 注意这里增加了对 Java 类型的完整限定名匹配
    public static java.lang.Double getAndRemoveCache(...);
    public static *** getShopItemByStack(...);
}

# --- 保护数据包 Record (Java 21) ---
-keepclassmembers record * { *; }