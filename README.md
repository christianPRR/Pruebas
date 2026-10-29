# Timestamp Camera

Cámara que estampa en cada foto la **fecha y hora del teléfono**, las **coordenadas GPS** y una **nota opcional**. Las fotos se guardan en la Galería (carpeta `Pictures/TimestampCamera`).

## Cómo obtener el APK (sin instalar nada en tu PC)

1. Crea una cuenta gratis en https://github.com y crea un repositorio nuevo (por ejemplo `TimestampCamera`).
2. Sube **todo el contenido de esta carpeta** al repositorio (botón "Add file" > "Upload files"). Asegúrate de que se suba también la carpeta oculta `.github`.
3. Entra a la pestaña **Actions**. Verás el flujo "Build APK" ejecutándose (tarda 3 a 6 minutos).
4. Cuando termine con una marca verde, ábrelo y descarga el archivo **TimestampCamera-apk** (viene en un .zip con `app-debug.apk` dentro).
5. Pasa el APK a tu celular, ábrelo y acepta "Instalar apps de fuentes desconocidas" si Android lo pide.

## Alternativa: Android Studio

Abre esta carpeta con Android Studio, espera a que sincronice y usa **Build > Build APK(s)**.

## Permisos

- Cámara (obligatorio)
- Ubicación (para las coordenadas; si lo niegas, la foto dice "Ubicación: buscando...")
