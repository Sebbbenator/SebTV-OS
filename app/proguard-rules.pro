# Al appens egen kode beholdes som den er (den er lille og bruger lambdas, manifest og JSON navne)
-keep class dk.selveste.launcher.** { *; }
# Ingen omdøbning, så stack traces i fejlloggen kan læses
-dontobfuscate
