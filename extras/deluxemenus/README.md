# Guía de encantamientos (DeluxeMenus)

Menú informativo, solo lectura, que explica el sistema de SuperEnchanter como una
guía por pasos (9 pasos). **No es parte del plugin** — es config de
[DeluxeMenus](https://wiki.helpch.at/helpchat-plugins/deluxemenus); se guarda aquí
como respaldo para que no viva solo en el servidor.

## Instalación

1. Copia `guia_encantar.yml` a `plugins/DeluxeMenus/gui_menus/`.
2. En `plugins/DeluxeMenus/config.yml`, dentro de `gui_menus:`, añade:
   ```yaml
   guia_encantar:
     file: guia_encantar.yml
   ```
3. `/dm reload`.

## Uso

Ábrela con `/guiaencantar` (alias `guiaencantamientos`, `encantarguia`).

Está pensada para DeluxeMenus 1.14+ (usa MiniMessage). Para cambiar los textos,
edita las `lore` del archivo y haz `/dm reload`. Si cambias el catálogo o el
balance del plugin, acuérdate de actualizar también los pasos que lo mencionen
(costes, monedas, rarezas que se gatean por Magia, etc.).
