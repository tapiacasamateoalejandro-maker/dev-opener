# Dev Opener

App mínima para TV (y celular) con botones que intentan abrir las **opciones de
desarrollador** de Android por varias rutas. Se maneja con el control remoto.

## Cómo obtener el APK sin instalar Android Studio (GitHub Actions)

1. Crea una cuenta en https://github.com (gratis) y un repositorio nuevo, por ejemplo `dev-opener`.
2. Sube todo el contenido de esta carpeta al repositorio (**Add file > Upload files**),
   incluida la carpeta oculta `.github`. Si no la deja subir, usa **Add file > Create new file**,
   escribe el nombre `.github/workflows/build.yml` y pega el contenido de ese archivo.
3. Ve a la pestaña **Actions**, elige **Compilar APK** y pulsa **Run workflow**.
4. Cuando termine (unos 3 a 5 minutos), entra a la ejecución y descarga `dev-opener-apk`
   al final de la página. Dentro del zip está `app-debug.apk`.

## Cómo compilarlo con Android Studio

Abre esta carpeta con Android Studio, espera la sincronización y usa
**Build > Build APK(s)**. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalarlo en la TV

1. Copia `app-debug.apk` a una memoria USB.
2. En la TV, permite instalar apps de fuentes desconocidas (en Ajustes o cuando lo pida).
3. Abre el APK desde un explorador de archivos de la TV e instálalo.
4. Abre **Dev Opener** y prueba los botones del 1 al 3. El 4 muestra la pantalla de
   información del dispositivo y el 5 solo funciona si se concedió un permiso especial por ADB.

## Limitaciones

Si el fabricante quitó o bloqueó esa pantalla de Ajustes, los botones 1 a 3 pueden no
abrir nada o cerrarse solos. En ese caso el siguiente paso es ADB.
