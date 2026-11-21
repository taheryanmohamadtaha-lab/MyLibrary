# Room و Compose قوانین خود را همراه کتابخانه می‌آورند. قوانین زیر فقط احتیاطی است.
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**
-keepattributes *Annotation*, Signature, InnerClasses
