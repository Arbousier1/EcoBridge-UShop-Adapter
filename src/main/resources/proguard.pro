# 基础混淆设置
-dontshrink
-dontoptimize
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# 保护插件主类
-keep public class top.ellan.middleware.EcoBridgeMiddleware { *; }

# 保护所有事件监听器
-keepclassmembers class * {
    @org.bukkit.event.EventHandler *;
}

# 保护经济钩子
-keep public class top.ellan.middleware.hook.UShopEconomyHook { *; }

# 保护拦截器及其买卖差价逻辑 API
-keepclassmembers class top.ellan.middleware.listener.ShopInterceptor {
    public static void init(...);
    public static void buildIndex();
    public static void clearAllCaches();
    public static int getItemSignature(...);
    public static double safeCalculatePrice(...);
    public static java.lang.Double getAndRemoveCache(...);
    public static *** getShopItemByStack(...);
}

# 保护数据包 Record
-keepclassmembers record * { *; }

# 忽略第三方库警告
-dontwarn **