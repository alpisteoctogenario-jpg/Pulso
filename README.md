# Pulso v4.1
## IMPORTANTE al subir a GitHub
El contenido de esta carpeta (settings.gradle.kts, app/, .github/ ...) debe quedar en la RAÍZ del repositorio,
no dentro de una subcarpeta "Pulso". La carpeta oculta .github debe subirse también (desde el móvil a veces se omite:
créala a mano en Add file > Create new file con la ruta .github/workflows/build.yml).
## Obtener el APK
Actions > ejecución más reciente (verde) > Artifacts > pulso-apk. La app muestra "Pulso v4.0" arriba si es esta versión.
## Primera instalación de esta versión
El APK va ahora firmado con una clave fija (app/pulso.keystore). Si Android dice que choca con la app instalada,
exporta antes la copia (Ajustes > Exportar), desinstala la antigua, instala esta e importa. A partir de aquí las actualizaciones se instalan encima.
