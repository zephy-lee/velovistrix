# kotlinx.serialization — @Serializable 클래스의 생성된 serializer 를 보존한다.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *** descriptor; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-if @kotlinx.serialization.Serializable class ** { static **$* *; }
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepattributes Signature, Exceptions

# androidx.security-crypto 가 Tink 를 끌어오고, Tink 는 컴파일 타임 전용
# errorprone 애노테이션을 참조한다. 런타임에는 필요 없는 클래스라 경고만 끈다.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.http.**
-keep class com.google.crypto.tink.** { *; }

# Retrofit 인터페이스의 시그니처(제네릭 반환 타입)를 보존한다.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Coil
-dontwarn coil3.**
# Tink 의 KeysDownloader 는 Joda-Time 을 참조하지만, 이 앱은 원격 키 다운로드를
# 쓰지 않으므로 해당 경로가 실행되지 않는다.
-dontwarn org.joda.time.**
