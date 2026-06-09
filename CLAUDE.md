# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# SuperEnchanter — Resumen del proyecto

Plugin de Paper (autor: Scrulius) que **reemplaza el yunque y la mesa de encantamientos vanilla**
por GUIs custom estilo Hypixel SkyBlock, con multi-economía, anti-dupe, y "librerías encantadas".

> **🔒 NORMA FUNDACIONAL — Plugin PRIVADO para el server DarkMines.** SuperEnchanter NO se
> comercializa ni se distribuye; es exclusivo del server **DarkMines** del autor. Esto **cambia los
> defaults y el diseño**: optimiza para la experiencia que DarkMines quiere **en vivo**, no para
> "seguro/desactivado por defecto para no sorprender a servers ajenos" (eso es mentalidad de plugin
> público) ni para compatibilidad con instalaciones de terceros (solo hay UNA instalación). Reglas:
> - **Defaults = lo que DarkMines quiere jugar.** Una feature acabada arranca ENCENDIDA con sus
>   valores deseados (p.ej. `success-chance.enabled: true`), no en `false` "por seguridad".
> - **No añadas knobs de config para terceros hipotéticos.** Configurabilidad solo donde el autor la
>   usaría de verdad para tunear; no sobre-ingenierices opciones "por si otro admin…".
> - **Backwards-compat con installs ajenos = irrelevante.** Puedes rebalancear defaults libremente
>   (respetando que el auto-merge no pisa los valores ya tuneados del autor; avisa si hace falta
>   regenerar el `config.yml`).
> - El fail-safe que apaga el server en error crítico encaja aquí: es el server propio del autor.

> **🔄 MANTÉN ESTE ARCHIVO ACTUALIZADO.** Cuando hagas un cambio significativo en el plugin
> (nueva feature, cambio de arquitectura/config, decisión de diseño, gotcha nuevo), actualiza la
> sección correspondiente de este `CLAUDE.md` en el mismo trabajo, antes de terminar. Mantenlo
> conciso (es un resumen, no documentación exhaustiva). Puedes refrescarlo a mano con `/update-summary`.

> **📦 SUBE SIEMPRE A GITHUB AL TERMINAR.** Tras cualquier trabajo (nueva feature, fix, cambio de
> config/docs), haz commit y push al repo privado como último paso, sin que el autor lo pida:
> ```
> git add .
> git commit -m "descripción concisa del cambio"
> git push
> ```
> El repo es **https://github.com/Scrulius/SuperEnchanter** (privado). Un mensaje de commit claro
> basta — no hace falta rama ni PR, push directo a `master`.

---

## Stack y build

- **Runtime objetivo:** Minecraft/Paper **26.1.2** (versionado year-based) — requiere **Java 25**.
- **Gradle** (`build.gradle.kts`). Coordenada Paper: `io.papermc.paper:paper-api:26.1.2.build.+`
  (o pin `26.1.2.build.NN-stable`). NO el viejo `-R0.1-SNAPSHOT`.
- `settings.gradle.kts` usa el plugin **foojay-resolver** para que Gradle auto-descargue el JDK 25
  (en local solo hay JDK 21; el 25 quedó en `~/.gradle/jdks/eclipse_adoptium-25-...`).
- `api-version: '26.1.2'` en `plugin.yml`.

### Comandos (Windows → usar `gradlew.bat`)
- **Compilar el jar:** `./gradlew.bat jar` → `build/libs/SuperEnchanter-1.0.0.jar`.
- **Correr todos los tests:** `./gradlew.bat test --console=plain`.
- **Un test concreto:** `./gradlew.bat test --tests "dev.scrulius.superenchanter.gui.enchanting.EnchantFormulasTest"`
  (o un método: `--tests "*EnchantFormulasTest.CumulativeCost*"`). Acepta comodines.
- **Build limpio:** `./gradlew.bat clean jar`. No hay linter aparte de `-Xlint:deprecation`
  (warnings en compilación; la DataComponent API es `@Experimental`, así que hay warnings esperables).
- Tras tocar tests/fórmulas, `test` basta; para desplegar, `jar` y copiar al server de pruebas.

### Dependencias
- **EcoEnchants** = dependencia DURA (`depend`), vía su **API real** (no reflexión).
  compileOnly `com.willfp:EcoEnchants` / `eco`.
- **MythicMobs** = `softdepend`, compileOnly vía `files(...)` al jar del server de pruebas
  (para librerías encantadas).
- Soft: Vault, PlayerPoints. (PacketEvents fue ELIMINADO — ver decisiones.)

---

## Servidor de pruebas

`C:\Users\Knopp\Desktop\server paper testeos\` — Paper 26.1.2 + Java 25 (ver `start.bat`).
Desplegar = copiar el jar a `plugins/`. **NUNCA hot-swap con el server encendido**
(corrompe el classloader de clases no cargadas → `ClassNotFoundException` luego;
nos pasó con AnvilGUI). Siempre **reinicio completo** tras desplegar.

> **🚚 NORMA: copia SIEMPRE el jar al server de pruebas al terminar.** Tras un `./gradlew.bat jar`
> exitoso, copia `build/libs/SuperEnchanter-1.0.0.jar` a
> `C:\Users\Knopp\Desktop\server paper testeos\plugins\` (sobrescribiendo) como último paso del
> trabajo, sin que el autor lo pida. (El server debe reiniciarse para que aplique — no hot-swap.)

---

## Arquitectura (paquetes en `dev.scrulius.superenchanter`)

- `SuperEnchanterPlugin` — main; inicializa config, hooks, manager, listeners, comando.
- `config/` — `PluginConfig` (config tipado + `ConfigUpdater` auto-merge), `MessagesConfig`,
  `SoundEffect`/`ParticleEffect` (records de efecto configurables: sonido con vol/pitch, partícula).
- `gui/` — `AbstractCustomGUI` (base anti-dupe), `gui/anvil/` (AnvilGUI + AnvilLogic + **AnvilFormulas**),
  `gui/enchanting/` (EnchantingGUI + EnchantingLogic + **EnchantFormulas** + BookshelfScanner),
  `gui/transfer/` (TransferGUI + TransferLogic — transferencia de encantamientos vía grindstone).
  Los `*Formulas` son matemática PURA y **Bukkit-free** (merge de niveles, curvas de coste/poder,
  roman) extraída para testearla sin servidor; las clases `*Logic` delegan en ellas.
- `command/` — `SuperEnchanterCommand` (árbol Brigadier de `/se`: reload/give/book/bookshelf/audit).
- `listener/` — GUIProtectionListener, BlockInteractListener (yunque + mesa + **grindstone**),
  VanillaBlockListener, BookshelfTrackingListener, **LootControlListener** (quita encantamientos
  "gratis" del loot natural).
- `integration/` — EcoEnchantsHook, MythicMobsHook, VaultHook, PlayerPointsHook.
- `economy/` — `CostType` (XP/VAULT/PLAYER_POINTS), `Cost` (importe + display, puro/Bukkit-free),
  `CostService` (canAfford/deduct/refund/balanceText + `effectiveCost`/`discountMultiplier` +
  `xpLevelsEquivalent`, multi-moneda compartido por yunque, mesa y transferencia), `XpMath`
  (curva vanilla de XP pura/testeada: puntos↔niveles, para el hint "≈ N niveles"). ⚠️ **La XP se
  cobra en PUNTOS reales, NO en niveles** (cobrar niveles era explotable: 8 niveles desde nivel bajo
  ≈ gratis en puntos). Cobro/reintegro vía API NATIVA de Paper:
  `calculateTotalExperiencePoints()` / `setExperienceLevelAndProgress(int)` (recalculan la curva
  vanilla; MockBukkit las implementa → testeado end-to-end). Display `Cost` XP = "1,250 XP" (puntos
  con agrupación); `balanceText` añade "(nivel N)".
- `util/` — ItemBuilder, MiniMessageUtil, EnchantmentHelper, CooldownManager,
  PendingItemStore, ConfigUpdater, EnchantedBookshelfManager, `AuditEntry` (DTO del registro +
  `ItemSnapshot` enriquecido), `AuditLog` (rastro de operaciones **estructurado** a `audit.jsonl`,
  un JSON por línea vía Gson; entry construida en main thread —`snap()` lee el ítem—, I/O async;
  rotación por tamaño; gateado por `audit-log.enabled`).
- `gui/audit/` — `AuditGUI` (visor paginado solo-lectura del audit; ver Comandos).

---

## Funcionalidades

### Anti-dupe (lo más importante)
Modelo "denegar por defecto, re-permitir solo lo seguro" en `AbstractCustomGUI.handleClick`:
- Todo click empieza cancelado.
- Tipos peligrosos SIEMPRE bloqueados: SHIFT, DOUBLE_CLICK, DROP, CONTROL_DROP, NUMBER_KEY,
  SWAP_OFFHAND, CREATIVE.
- Inventario inferior: clicks normales permitidos.
- Slots de input: solo LEFT/RIGHT plano → lo mueve **vanilla** (NO reimplementamos el cursor a
  mano; eso era la fuente clásica de dupes). Luego `updatePreview()`.
- Resto de slots del top = botones → `onSlotClick`, siguen cancelados.
- Drags que tocan el top → cancelados. Ítems devueltos al cerrar/desconectar (AtomicBoolean
  anti doble-cierre).
- **Crash-persistence**: `PendingItemStore` guarda los ítems de los slots de input en
  `pending-items.yml` y los devuelve al reconectar (gateado por `anti-dupe.crash-persistence-enabled`).

### Lenguaje visual unificado de las GUIs (yunque + transferencia)
Las dos comparten layout para que se lean igual: fondo **negro** (`BLACK_STAINED_GLASS_PANE`),
**dos inputs a los lados con etiqueta de color encima** (slots 10/16 etiqueta, 19/25 input),
**botón de acción centrado** (slot 40), info (45) + cerrar (53) abajo. Nada de gris-sobre-gris.

### Yunque (AnvilGUI) — rediseñado + validación unificada
- Flujo **objeto (cian, izq) + sacrificio (naranja, der) → resultado (centro, slot 31) → Forjar
  (slot 40)**. El slot de resultado es el **único indicador de estado** (ítem fusionado = válido /
  barrera con conflictos = incompatible / placeholder = vacío); se eliminó el panel indicador
  redundante. Botón "Forjar" dedicado (clicar el resultado NO forja).
- **Validación unificada (clave)**: el merge (`AnvilLogic.calculateResult`) recibe un
  `AnvilEnchantGate` inyectado (interfaz funcional `Enchantment → nivel máx permitido`, `0`=rechazado).
  `AnvilGUI.gateFor(target)` lo construye desde `EnchantingLogic.analyze(target)` — la MISMA fuente
  que la mesa y la transferencia — así el yunque respeta targets/conflictos/requeridos/type-limit/
  **blacklist** + el cap de `enchantment-overrides`, no solo las reglas vanilla. Antes el yunque era
  un bypass (p.ej. colar un encantamiento blacklisteado fusionándolo). El gate se **cachea por ítem**.
  Patrón de DI elegido para que la matemática de merge siga siendo pura y testeable sin eco: la
  sobrecarga de 3 args usa `AnvilEnchantGate.ALLOW_ALL` (los tests MockBukkit no cambian); la de 4
  args aplica el gate. Los nombres de encantamiento en la lista de conflictos usan
  `EcoEnchantsHook.displayNameOrFallback` (localizado por eco, NO `prettyName` en inglés).
- Coste XP/Vault/PlayerPoints con overrides por ítem, vía `CostService` compartido.
- NO renombra ni repara durabilidad (decisión: el server RPG desactiva mending/grindstone).
- **Identidad de ítem (`AnvilGUI.isMergeableSacrifice`/`sameIdentity`)**: el sacrificio solo vale si es
  un **libro encantado** O un ítem de **identidad idéntica** al objeto (mismo material **+ CMD + id de
  MythicMobs**). Bloquea combinar dos ítems que solo comparten material base (p.ej. "espada de diamante"
  + "espada de diamante concentrada"), que vanilla nunca permitió. Se valida en preview Y en forge (el
  botón es clicable siempre). Barrera `anvil.different-items-*` cuando no casa.

### Mesa de encantamientos (EnchantingGUI) — 2 niveles, COMPACTA (27 slots / 3 filas)
**Rediseño hardcore (2026-06)**: progresión **secuencial obligatoria** — el único nivel comprable es
el SIGUIENTE (I→II→III…, sin saltos); **el icono del encantamiento ES el botón de intento** (se
eliminó el tier de niveles). Cada clic = UN intento con su tirada, cobrado aunque falle.
1. **Categorías** = tipos de EcoEnchants (`EcoEnchantsHook.getTypeId`).
2. **Encantamientos** de la categoría: el icono muestra barra de % de éxito, % exacto de maldición,
   riesgo de downgrade, coste y poder del siguiente nivel; clic = intentar ese peldaño
   (`EnchantingLogic.nextStep` → `NextStep{level, stepCost, requiredPower}`).
- Botón cerrar hace de "atrás" (sube un nivel).
- **Layout 54 slots** (fondo negro `BLACK_STAINED_GLASS_PANE`, como yunque/transferencia): cabeza de
  stats (4) centrada arriba; icono mesa (19) + **input (20)** a la izquierda; **grid de ofertas 4×2**
  (slots 22-25 / 31-34); poder (48), cerrar/atrás (49), guía (50), paginación (46/52). El slot de
  sello (28/29) desapareció con los sellos. ⚠️ Se probó una versión compacta de 27 slots y se
  REVIRTIÓ (feedback del autor: demasiado apretada/confusa) — no volver a compactarla.
- **Barras de progreso en texto** (sin resource pack, glifo `■` ×10): % de éxito (icono de
  encantamiento, gradiente verde), nivel de Magia (cabeza de stats, `{bar} nivel/max` — el max se lee
  NATIVO de `Skill.getMaxLevel()` vía `MagiaService.maxLevel()`), poder de librerías (icono de poder).
  Render puro en `EnchantFormulas.progressBar/filledSegments` (testeado); los tags MiniMessage del
  relleno/vacío son constantes (gradiente se auto-cierra).
- **Estilo unificado de iconos**: *etiquetas de slot* = `gradiente+negrita+➜`; *paneles de info*
  (poder, stats, guía) = `gradiente+negrita+glifo` (✦ / 📖); *botones* = `color+negrita+glifo`
  direccional. Viñetas `✦` apagadas (`#6C7293`), prosa en `#A8B2D1`, acción `▶ Clic para intentar…`.
- **Bloqueados se muestran con motivo**: `EnchantingLogic.analyze()` → `AnalyzedEnchant` con
  `BlockReason` (NONE/MAXED/CONFLICT/MISSING_REQUIRED/TYPE_LIMIT). Bloqueados = gris con el porqué; no
  se pueden abrir (eso también evita saltarse el chequeo de conflicto). Usa el modelo real de
  EcoEnchants (targets/conflicts/required/typeLimit).
- **Pre-índice (`EnchantmentIndex`, perf)**: toda la metadata de encantamiento **independiente del
  ítem** (typeId/rareza/nombre/maxLevel/sets de requerido y conflicto + `conflictsAll`/typeLimit +
  nombre de categoría + flag de blacklist) se **precomputa UNA vez al arrancar** en una estructura
  inmutable (`EnchantmentIndex.build`), y se reconstruye en `/se reload`. Antes `analyze()` recorría
  todo el registry POR ítem y por candidato **asignaba un `ArrayList` nuevo** en `getConflicts`/
  `getRequired` (O(presentes) veces) — ahora `analyze()` solo hace lo dependiente del ítem
  (`appliesToItem` + clasificación con lookups O(1) en los sets ya hechos). Lo guarda el plugin
  (`getEnchantmentIndex()`, build eager en `onEnable` tras el self-check, lazy como fallback; salta
  bajo MockBukkit porque toca eco). El GUI sigue cacheando el `analyze()` por ítem encima de esto.
- **Requisitos de uso visibles ANTES de encantar**: los iconos (tier-2 lista y tier-3 niveles)
  añaden las **líneas de requisito** del encantamiento vía `EcoEnchantsHook.getRequirementLines(ench)`
  (`EnchantingLogic.appendRequirementLines`). ⚠️ Gotcha clave: los `not-met-lines` (p.ej. "Requiere
  Combate XV" de un gate `has_skill_level`) **NO** forman parte de `getFormattedDescription` — en el
  ítem real los pinta el módulo de display de EcoEnchants llamando a
  `libreforge.ItemProvidedHolder.getNotMetLines(player)`. Como libreforge NO es dep de compilación,
  los leemos **del config del encantamiento**: `Config.getSubsections("conditions")[].not-met-lines`
  (busca en el top-level de la condición Y bajo `args`; en este catálogo van bajo `args`). ⚠️ Gotcha
  del config: `UtilKt.wrap(ench)` NO devuelve el modelo `EcoEnchantBase`, devuelve un **proxy de
  registro** (`EcoEnchantsCraftEnchantment`) o `VanillaEcoEnchantLike`; `getConfig()` está en el
  impl/proxy pero NO en la interfaz `EcoEnchant` → se obtiene por **reflexión** (`EcoEnchantsHook.configOf`).
  Cacheado por encantamiento. Se muestran SIEMPRE (no solo cuando el jugador no cumple), así el
  jugador conoce el requisito. La descripción de los iconos usa la
  sobrecarga player-aware `getDescription(ench, level, player)` (solo afecta a placeholders
  dependientes del jugador; los requisitos NO vienen de ahí).
- **Lag del lore tras encantar arreglado**: al aplicar un encantamiento con éxito, `EnchantingGUI`
  llama `com.willfp.eco.core.display.Display.display(enchanted, player)` antes de poner el ítem en el
  slot. Eso fuerza el render de eco YA en vez de esperar al refresco de su caché (`display-frame-ttl`
  en `plugins/eco/config.yml`, ~17 ticks) — ese caché era el "ratazo" antes de que apareciera el
  lore/requisito. El display de eco es revertible (revierte al leer), así que guardar un ítem
  "displayed" en el slot de input es seguro (el jugador nunca ve lore horneado).
- **Blacklist unificada** (`PluginConfig.isEnchantDisabled`): acepta key completa, ruta,
  `#curses` (vanilla + tipo curse de EcoEnchants), `#type:<id>`. NO existe `disable-curses`.
- **Coste SOLO XP, por intento, en PUNTOS reales, curva GEOMÉTRICA**: cada clic cobra SOLO ese
  peldaño (no hay coste acumulativo: `cumulativeCost` fue eliminada con el salto de niveles).
  ⚠️ **Algoritmo "del copón" (`EnchantFormulas.geometricCost`, testeado)**:
  `coste(nivel) = min(cap, base · multRareza · level-growth^(nivel-1) · primaRemate)` — crecimiento
  **geométrico** (cada nivel × `level-growth` el anterior → los niveles altos EXPLOTAN; la curva
  polinómica vieja `base + mult·nivel^1.15` apenas subía y x5 solo escalaba, no cambiaba la forma).
  La **prima de remate** (`final-level-multiplier`, ×1.5) se aplica cuando el nivel es el MÁXIMO del
  encantamiento (`nextStep` pasa `finalMult` solo si `level==maxLevel`). El cap se aplica ANTES de
  redondear → sin overflow de `int`. Defaults mesa: `base 500 / growth 2.0 / prima 1.5 / cap 150000`
  + rarezas **AGRESIVAS** `1/2.5/6/15/40` (antes 1/1.7/2.5/4/6). Referencias (puntos): común V remate
  = 12.000; épico V = 72.000; divino I = 20.000 (un divino max-1 con remate = 30.000 ≈ nivel 92);
  divino tardío toca el cap 150.000 ≈ nivel 200. ⚠️ **Los importes son PUNTOS de XP, no niveles**;
  el icono traduce el coste a **"≈ N niveles para ti"** con la XP real del jugador
  (`CostService.xpLevelsEquivalent` + `XpMath`; claves `enchant-icons.lore-cost-levels(-sub1)`). Los
  **reactivos materiales fueron ELIMINADOS** (`Reagent` ya no existe) y `rarity-cost-type` quedó
  **vacío** (divino ya NO se cobra en PlayerPoints; su premium es el gate de Magia 35 + poder 260+ +
  25% base + 8% maldición + curva geométrica ×40). El yunque (`base 250 / por-nivel 600 / cap 15000`,
  sigue lineal — coste por operación, no por curva de rareza) y la transferencia (misma curva
  geométrica, `base 400 / growth 1.8 / cap 60000`, sin prima) también en puntos.
- **Poder por rareza**: `min(maxPower, floor + nivel*step)` por rareza (`enchanting.rarity-power`).
  El poder gatea la MAGNITUD, no la fracción de nivel.
- **Overrides por encantamiento** (`enchanting.enchantment-overrides`, por key completa o ruta):
  `max-level` (capa niveles), `xp-cost` (coste fijo), `required-power` (poder fijo),
  `cost-multiplier`. Aplicados en `nextStep`/`cappedMaxLevel`.
- **Iconos/nombres de categoría configurables**: `enchanting.category-icons` (material por tipo +
  `default`); `category-names`/`rarity-names` en **messages.yml** (localización, fallback title-case).
- **Single-level sin romano**: un encantamiento con maxLevel 1 se muestra "Vitalidad", no
  "Vitalidad I". Los iconos muestran además **rareza** (`lore-rarity`) y progreso de nivel
  (`lore-level`).

### Transferencia / extracción de encantamientos (grindstone repurposado)
Banco dual abierto interceptando el grindstone (`BlockInteractListener` + failsafe en
`VanillaBlockListener`, gateado por `transfer.enabled`). El donante SIEMPRE **se consume entero**
(mover, NO copiar → no infla poder total). **Multi-selección: UNA operación, te llevas los que
elijas, el donante (con lo NO elegido) se destruye** — `selected` es un `Set<Enchantment>`, el botón
ejecuta TODOS los seleccionados de golpe. El modo lo decide si hay destino o no:
- **Destino = objeto** → **transferir** los encantamientos elegidos al destino.
- **Destino = LIBRO normal** (`Material.BOOK`) → **extraer** los elegidos a **UN** libro encantado (con
  varios stored enchants; gateado por `transfer.allow-extract`). **Consume 1 libro** (el slot de destino
  pasa de `BOOK` a `ENCHANTED_BOOK`; si había stack, devuelve el sobrante y el libro encantado va al
  jugador). `computeExtractOffers` lista los del donante **excepto maldiciones** (callejón sin salida) y
  `extractToBook(List)` construye el libro. **Coste extra**: `transfer.extract-cost-multiplier` (default
  2.0) — extraer crea un libro vendible, cuesta más que mover. `TransferGUI.extractMode` (= destino es
  libro) cambia botón+acción.
- **Validación 100% reutilizada**: `TransferLogic.computeOffers` corre `EnchantingLogic.analyze()`
  sobre el DESTINO y cruza con los encantamientos del donante. Así respeta EXACTAMENTE las reglas
  de la mesa (targets/conflictos/requeridos/type-limit/blacklist) sin duplicar nada. `TransferBlock`
  añade `NOT_APPLICABLE` (no targetea / blacklisted → ausente del analyze) y `ALREADY_OWNED`
  (destino ya tiene nivel ≥; nivel resultante = `max(donante, destino)`).
- **Coste** vía `EnchantFormulas.geometricCost` (POR encantamiento, sin prima de remate; el botón cobra
  la **suma** de los seleccionados, `TransferGUI.totalCost`). Sección `transfer` (cost-type,
  base-cost/level-growth/max-cost, `use-rarity-multiplier`, `require-same-material`). **Más barato que
  la mesa a propósito** (`base 400 / growth 1.8 / cap 60000`, en PUNTOS de XP): mover/extraer recupera
  algo ya creado (y destruye el donante), no debe costar más que encantar de cero.
- **UX tipo yunque**: clic en un encantamiento lo **togglea** (multi-select); un botón dedicado
  ejecuta todos (un misclic nunca destruye el donante). 2 slots de input, anti-dupe heredado de
  `AbstractCustomGUI`. Mensajes/botón usan `{count}` (no por-encantamiento).
- Permiso `superenchanter.transfer.use` (default true). NO testeable con MockBukkit (usa EcoEnchants
  vía analyze, igual que la mesa); la matemática de coste sí está cubierta por `EnchantFormulas`.

### Librerías Encantadas (bookshelves marcadas) — feature estrella
Objetivo: una **bookshelf vanilla REAL** (sin resource pack) que da más poder y es detectable.
- El item es de **MythicMobs** (`Id: BOOKSHELF`, `Options.Placeable: true` — OBLIGATORIO, MM
  bloquea el place por defecto). Al colocarlo pone una bookshelf normal.
- Como Minecraft no guarda identidad por bloque, la guardamos nosotros: `EnchantedBookshelfManager`
  marca la coordenada en el **PDC del chunk** (`"x,y,z=mmid;..."`, persiste con el mundo).
- `BookshelfTrackingListener` la hace **indestructible salvo picándola**:
  - place → marca (si el id MM está en config y es bookshelf).
  - break → `@EventHandler(priority=HIGHEST, ignoreCancelled=true)`; suprime el drop vanilla en el
    acto y **difiere 1 tick** la entrega del item MM (`MythicMobsHook.createItem`) + desmarca,
    SOLO si el bloque dejó de ser bookshelf. Esto evita un **dupe**: si un protector (WorldGuard,
    Towny…) cancela el break DESPUÉS de nosotros, sin el diferido entregaríamos el item y la
    librería seguiría en pie.
  - pistones → cancel; explosiones (TNT/cama) → se sacan de la blockList; fuego → cancel;
    enderman/wither/etc (`EntityChangeBlockEvent`) → cancel.
  - **Perf**: `getMark` corta por material ANTES de tocar el PDC (una marca solo vive en una
    bookshelf, por construcción), y `anyMarked`/`protect` cachean las marcas por chunk
    (`getMark(block, cache)`). Así un blast de TNT no re-parsea el PDC por cada bloque de tierra.
  - Único hueco: WorldEdit/`/fill`/`/setblock` no disparan eventos → el escáner **se autocura**
    (si la marca no apunta a una bookshelf, la limpia; nunca miente en el poder).
- `BookshelfScanner` da `config.getEnchantedBookshelfPower(id)` a las marcadas (en vez del 1
  normal), cacheando las marcas por chunk durante el escaneo.
- **Air-gap = line-of-sight real** (`BookshelfScanner.lineCells`, pura/testeada): la línea recta
  mesa→bloque debe pasar solo por aire; un bloque de poder tras una pared no cuenta. Más estricto que
  el viejo heurístico (que solo miraba la celda pegada) → librerías muy empaquetadas pueden perder algo
  de poder vanilla (las encantadas son la fuente real, impacto menor).
- Config `enchanting.enchanted-bookshelves: { libreria_encantada: 10 }` (un solo tipo, +10 poder;
  el item MM es `CHISELED_BOOKSHELF`, se sella con libros y se bloquea su click derecho — ver abajo).
- **Chiseled selladas**: la librería es una `CHISELED_BOOKSHELF`. Al colocarla (`sealIfChiseled`)
  se rellena el inventario del tile con libros + se fuerzan las flags `slot_occupied` (respaldo).
  `onInteract` cancela el click derecho sin agachado (no se sacan libros); **construir contra ella
  exige agacharse** (limitación del CLIENTE: un bloque interactuable solo deja colocar con shift, no
  hay forma server-side) → si llevas un bloque sin agacharte, actionbar `library-sneak-build`.

### Control de loot (no hay encantamientos "gratis" en loot natural)
`LootControlListener` (gateado por `loot-control.enabled`, default ON) evita que se consigan
encantamientos a coste 0 en cofres de estructuras (End/mansiones/trial chambers…), pesca y drops de
mobs — la obtención pasa SIEMPRE por la mesa custom. Dos acciones independientes:
- `remove-enchanted-books` → elimina los `ENCHANTED_BOOK` del loot.
- `strip-equipment-enchantments` → quita los encantamientos de CUALQUIER ítem (herramientas, armas Y
  armaduras — usa `item.getEnchantments()`, no filtra por tipo), dejando el ítem LIMPIO (no lo borra).
  Quita vanilla y EcoEnchants (`item.removeEnchantment`); salta los libros (los gobierna la opción anterior).
Cubre `LootGenerateEvent` (cofres/estructuras/pesca) y, con `include-mob-drops: true`,
`EntityDeathEvent` (equipo encantado que sueltan los mobs). `disabled-worlds: []` excluye mundos.

### Encantamientos prohibidos globalmente (purga — sucesor de AntiMending) — `banned-enchantments`
Absorbe el viejo plugin standalone **AntiMending** (`Scrulius/AntiMending`) generalizado a una lista
de keys configurable, default `[mending]` (default ON). `BannedEnchantmentListener` purga los
encantamientos vetados de CUALQUIER ítem para que no existan en el server (mending rompería la
economía de encantar con reparación por XP). La mesa ya no lo ofrece (blacklist) y el yunque vanilla
está bloqueado; esto cierra los vectores restantes (ítems previos, libros, creativo, pesca, pickups):
- `keys` (default `[mending]`) → `PluginConfig.isEnchantmentBanned(ench)` compara por key normalizada
  (`minecraft:` implícito; admite keys completas y de EcoEnchants). `strip()` quita held + stored
  (`EnchantmentStorageMeta`).
- `purge-player-inventories` (ON) → strip en join / `InventoryOpenEvent` / click. ⚠️ El strip del
  **click se difiere 1 tick** (`runTask`) — mutar ítems mid-click rompe el tracking de Bukkit y dupea
  (sobre todo en creativo); mismo patrón para `InventoryCreativeEvent`.
- `block-xp-repair` (ON) → cancela `PlayerItemMendEvent` (la XP nunca repara, aunque algo se colara).
- pickups (`EntityPickupItemEvent`) y pesca (`PlayerFishEvent`) siempre saneados con la feature ON.
- El loot NO se duplica aquí: lo cubre `LootControlListener` (que quita TODOS los encantamientos del
  loot natural).

### Tradeos de aldeano — `villager-trades`
`VillagerTradeListener` (default ON) cancela `VillagerAcquireTradeEvent` cuando el resultado es un
**libro de cualquier tipo** (`block-book-trades`, ON: BOOK/ENCHANTED_BOOK/WRITABLE/WRITTEN/KNOWLEDGE)
→ los bibliotecarios no venden encantamientos "gratis" saltándose la mesa custom. Cancelar al
adquirir impide que la receta llegue siquiera a existir en el aldeano.

### Descuentos de coste por permiso
`CostService.effectiveCost(player, cost)` aplica un descuento por permiso al coste que se
**muestra Y se cobra** (lo llaman las 3 GUIs justo al obtener el `Cost`). `superenchanter.cost.bypass`
= gratis; `superenchanter.cost.discount.<n>` = -n% (gana el mayor). Gateado por `cost-discounts.enabled`.

### Skill "Magia" (bucle de profesión vía SuperCore→EcoSkills) — `enchanting.magia`
Convierte la mesa de **sumidero** en **bucle RPG**: encantar da XP de una skill de EcoSkills, cuyo
nivel mejora el propio encantar. Diseño completo en [`docs/PLAN_MAGIA.md`](docs/PLAN_MAGIA.md).
**Requiere SuperCore** (puente a EcoSkills); si falta, Magia se desactiva sola (degrada limpio).
- `integration/MagiaService` — lee `enchanting.magia`; solo se **construye** si `SuperCore` está
  presente (`SuperEnchanterPlugin.onEnable` lo gatea con `isPluginEnabled("SuperCore")`, así
  MagiaService nunca se carga sin SuperCore → no `NoClassDefFoundError`). `isEnabled()` exige además
  que EcoSkills esté vivo (`SuperCore.ecoSkills().isEnabled()`). Llama a la API vía
  `dev.scrulius.supercore.api.SuperCore` (compileOnly al jar de SuperCore en `build.gradle.kts`;
  `SuperCore` en `softdepend` del `plugin.yml` para el orden de carga).
- `skill-id: enchanting` por defecto: **reutiliza la skill nativa `enchanting`** de EcoSkills
  (tematizada como "Magia"). Su XP la damos NOSOTROS por API — la mesa custom NO dispara el encantar
  vanilla del que viviría esa skill (y `VanillaBlockListener` bloquea la mesa vanilla → cero
  doble-conteo). ⚠️ **Damos la XP por la vía NATURAL** (`gainSkillXp` = `EcoSkillsAPI.gainSkillXP`),
  NO la raw `giveSkillXP`. **Por qué natural**: dispara `PlayerSkillXPGainEvent` y aplica los
  multiplicadores; eso es lo que hace funcionar **dos** cosas que escuchan ese evento — el efecto
  libreforge `skill_xp_multiplier` (armadura de mago) **y** el booster `ecoskills:experience` de
  **AxBoosters** (multiplica `getGainedXP`/`setGainedXP` en el evento; con la raw se lo saltaría →
  ⚠️ probado: la raw ROMPE AxBoosters). El sufijo `{magia}` muestra la XP **base** (pre-multiplicador);
  con armadura/booster el jugador recibe más.
  ⚠️ **El solapamiento de action bars se resuelve en el SERVER, no en código**: la vía natural manda la
  action bar de ganancia propia de EcoSkills (`skills.gain-xp.action-bar`, GLOBAL, sin toggle por skill)
  que chocaba con la de la mesa → **desactivada** en `EcoSkills/config.yml` (`enabled: false`). **Patrón
  de diseño del ecosistema**: EcoSkills es el "libro de cuentas" (niveles + recompensas + mensaje de
  SUBIDA de nivel, que SIGUE activo); el feedback de **ganancia** de XP lo da **cada plugin de contenido**
  (SuperEnchanter ya lo hace con el sufijo `{magia}`; futuros SuperMines/CustomFishing igual). El sonido
  de orbe (`gain-xp.sound`) se dejó puesto (no choca). **Lado A (server, HECHO)**: `EcoSkills/skills/enchanting.yml`
  reescrito como "Magia" — `name: Magia`, `xp-formula: ceil(12*nivel^2)` (max-level 50, ~515k XP
  acumulada), `xp-gain-methods: []` (la XP la da SOLO la API), única recompensa `wisdom levels:1`
  (= +1 maná máx/nivel; los efectos nativos second_chance/reimbursement/overcompensation se QUITARON
  porque solo disparan al encantar vanilla = muertos aquí). El `gui.lore` y los `reward-messages`
  (por nodo: "lo que TENDRÁS a este nivel") muestran las 4 sub-habilidades con valores reales vía
  placeholders (ver abajo) + placeholders custom de la skill (`ex/co/re` = `floor(min(tope,
  nivel*per-level))`, espejan los topes de MagiaService). Hex de paleta, líneas cortas (evitan
  palabras huérfanas por re-wrap del cliente). Maná visible en el panel de `/skills`
  (`EcoSkills/config.yml` → `gui.player-info.lore` con `%ecoskills_mana%/%ecoskills_mana_limit%`).
- **Carriles aplicados leyendo `magia.level(player)`** en `EnchantingGUI.handleLevelClick`:
  - **C1 éxito**: `successBonus` (pts %) se suma al `boosterPercent` antes de `EnchantFormulas.
    effectiveChance` (+0.5%/nivel, tope +25%).
  - **C2 coste**: `MagiaService.applyDiscount(player, cost)` se aplica **encima** del descuento por
    permiso (`CostService.effectiveCost`) → combinación multiplicativa. Cableado en los **3 puntos
    de coste de la mesa**: cobro (`handleLevelClick`), icono de nivel
    (`EnchantingLogic.createLevelOfferIcon`) y mensaje "no te alcanza" (`sendResourceError`), para
    que lo mostrado = lo cobrado (−0.4%/nivel, tope −20%).
  - **C5 gating** (`gating.enabled`, default **ON**): HARD-GATE por nivel de Magia, **solo
    legendario (20) y divino (35)** — común/raro/épico NO se gatean (su "puerta" es el % de éxito,
    no un candado, para no atascar al jugador en rarezas bajas). El icono de nivel se renderiza como
    **barrera con el nivel requerido** (`enchant-icons.locked-*`) en `createLevelOfferIcon` ANTES de
    clicar (no falla al clicar), y `handleLevelClick` lo re-bloquea con actionbar
    `enchanting.magia-locked`. Si Magia se desactiva (sin SuperCore), `canEnchant` devuelve true → el
    gate cae solo (no bloquea a nadie). ⚠️ Diseño: la rareza alta es un **gamble de % de éxito**
    (`by-rarity` rebalanceado a 80/65/35/20/10), no solo un candado; divino premium a 10% → el Sello
    Divino (garantía) es la jugada segura, el Reembolso Arcano mitiga el fallo.
- **XP por operación** (`grantXp`, devuelve la XP dada): `niveles_ganados × xp(rareza)` + una
  fracción (`xp-fail-fraction`, 0.25) si un peldaño falló ("aprendes del error"). Solo si se cobró
  algo. **Feedback**: el action bar de encantar (success/partial/fail/cursed) anexa el sufijo
  `enchanting.magia-xp-suffix` (`{magia}` → "+N ✦ Magia") cuando se ganó XP. ⚠️ El número del sufijo
  es la XP **base** (pre-multiplicador); con armadura de mago o un booster de AxBoosters el jugador
  recibe más (lo aplica eco en el evento `PlayerSkillXPGainEvent`, ver arriba).
- **Encantamientos "build de mago"** (catálogo EcoEnchants, NO código del plugin): aceleradores de la
  XP de Magia vía efecto `skill_xp_multiplier` (`skills: [enchanting]`). `tunica_de_mago` (raro,
  chestplate, ×1.15 = +15%) y `sombrero_de_mago` (legendario, helmet, ×1.25 = +25%); ambos puestos
  apilan **multiplicativo** → ×1.4375 (~+44%). Solo aceleran el grindeo de la skill (no dan poder de
  combate/economía) → reward autocontenido, end-game de la profesión. Funcionan SOLO porque la XP va
  por `gainSkillXp` (natural), que dispara el evento que escucha el efecto `skill_xp_multiplier`.
  Se dan con `/se book tunica_de_mago 1` / `/se book sombrero_de_mago 1`.
  Files: `Ecoenchants_Old/enchants/{raro/tunica_de_mago,legendario/sombrero_de_mago}.yml` (catálogo
  canónico) + desplegados en `plugins/EcoEnchants/enchants/` del server de pruebas.
- **Reembolso Arcano** (sub-habilidad, `enchanting.magia.refund`): al FALLAR un peldaño, prob.
  `min(max-percent, nivel*per-level)` (default 0.6/nivel, tope 30% a nivel 50) de **recuperar el
  coste** de ese peldaño — `CostService.refund` (XP: `setLevel+`; Vault: `deposit`; PP: `give`).
  Suaviza el sumidero del `success-chance`. Feedback: `enchant-fail-refund` (primer peldaño) o
  sufijo `refund-suffix` (`{refund}` en el parcial).
- **Visibilidad de los bonus = cabeza de estadísticas (NO por icono)**: los bonus de Magia ya NO se
  listan bajo cada encantamiento (se quitó `appendMagiaLore`/`magia-line`); el coste/probabilidad del
  icono ya salen CON los bonus aplicados (`effectiveCost`+`applyDiscount`, y `appendChanceLore` suma el
  `successBonus`). Todos los bonus se reúnen en una **cabeza de jugador** ("Mis estadísticas",
  `SLOT_STATS=4`, centro arriba, con la skin del viewer vía `ItemBuilder.skullOwner`): nivel de Magia,
  +éxito, −coste, reembolso y **+% XP de Magia** (`enchant-icons.stats-head-*`). El **maná NO** se lista
  aquí (lo gastan los hechizos, no la mesa). Solo aparece si Magia está activa; se refresca en
  `fillDecoration` y tras encantar (puede subir de nivel). Helpers de display:
  `MagiaService.discountPercent/refundChance/manaBonus/xpBonusPercent`. El **+% XP de Magia**
  (`xpBonusPercent`) se lee **NATIVO** de EcoSkills vía `SuperCore.ecoSkills().effectiveSkillXpMultiplier`
  = `getSkillXPMultiplier` (booster por permiso, global) × el total del efecto libreforge
  `skill_xp_multiplier` para la skill (`EffectSkillXpMultiplier.INSTANCE.getMultiplier(dispatcher, skill)`,
  el mismo valor que `gainXP` aplica). Así refleja **cualquier** fuente automáticamente (armadura de mago,
  boosters comprados, otros plugins que usen ese efecto) sin lista hardcodeada. Se lee por **reflexión**
  (libreforge es plugin de runtime, NO dep de compilación — está en `plugins/libreforge/versions/`;
  `getMultiplier` es `protected` → `setAccessible`); degrada a ×1.0 (=+0%) si algo falla. ⚠️ Limitación:
  un plugin que suba XP con su PROPIO listener de `PlayerSkillXPGainEvent` (en vez del efecto/permiso
  estándar) NO se previsualiza.
- **Placeholders (`integration/SuperEnchanterPlaceholders`)**: expansion de PlaceholderAPI
  `superenchanter` (softdepend + compileOnly al jar de PAPI; solo se registra/carga si PAPI está,
  guarda en `onEnable`). Expone los bonus de Magia con valores REALES para configs ajenas (la
  descripción de la skill en EcoSkills, scoreboards…): `%superenchanter_magia_active/level/success/
  discount/refund/mana_bonus%`. Los carriles viven aquí, así que EcoSkills no puede calcularlos solo
  → la skill `enchanting.yml` (Lado A) usa estos placeholders en su `gui.lore` para mostrar la realidad.
- ⚠️ NO testeable con MockBukkit (usa EcoSkills vía SuperCore, igual que el resto de la mesa).
- ⚠️ **EcoSkills no recarga los magic types**: `/ecoskills reload` lanza `WARN "price factory already
  registered for mana"` (inofensivo, pero aborta la recarga de la config de maná). Cambios en
  `enchanting.yml`/maná exigen **reinicio completo** del server, no `/ecoskills reload`.

### Encantamiento probabilístico + DOWNGRADE (mesa) — `success-chance` (sin sellos)
**Activado por defecto** (`enchanting.success-chance.enabled: true`; `false` = kill-switch que vuelve
al 100% garantizado). Cada **intento** (clic = un peldaño) tiene una **probabilidad de éxito por
rareza**; un **fallo consume el coste igual** — y puede además **bajar un nivel** (downgrade).
- **Curva**: `prob = clamp(base(rareza) + bonusMagia, 0..100)` (`EnchantFormulas.effectiveChance`,
  pura/testeada). **Los sellos de rareza fueron ELIMINADOS** (rediseño hardcore 2026-06): el récord
  `Booster`, `getBoosterPercent/getBooster/getSuccessBoosterIds`, el slot de potenciador y
  `success-chance.boosters` ya NO existen. El único sello superviviente es el **Sello Purificador**
  (yunque, quitar maldiciones). Las dos mitigaciones que quedan: **nivel de Magia** (+% éxito) y la
  **fusión en yunque de dos ítems idénticos** (ruta determinista que consume un segundo ítem entero —
  la "garantía" cara que sustituye a los sellos).
- **Downgrade (`success-chance.downgrade`, default ON)**: al FALLAR, tirada extra por rareza
  (`comun 0 / raro 15 / epico 20 / legendario 25 / divino 30`) de que el encantamiento **baje un
  nivel**; a nivel I se **pierde** (`EnchantmentHelper.removeEnchantment`). Solo aplica si había nivel
  que perder (fallar 0→I solo quema el coste). El % y el nivel de caída se muestran en el icono
  (`lore-downgrade`/`lore-downgrade-lose`). Feedback `enchant-downgrade`/`enchant-downgrade-lost` +
  sonido propio (`sounds.enchant-downgrade`), audit **`ENCHANT-DOWN`**. ⚠️ **Balance acoplado**: la
  deriva esperada por intento es `p − (1−p)·d`; con las bases antiguas (divino 10%) salía NEGATIVA →
  por eso `by-rarity` se rebalanceó a **85/70/55/40/25**. Si tocas bases o downgrade, re-comprueba que
  la deriva quede positiva para cada rareza (con el bonus de Magia del gate incluido).
- **Tirada por clic** (`EnchantingGUI.handleEnchantClick`): cobra el `stepCost` (efectivo con
  descuentos), tira `ThreadLocalRandom`; éxito → aplica nivel + tirada de maldición; fallo → tirada de
  Reembolso Arcano (independiente) + tirada de downgrade. Maldición (en éxito) y downgrade (en fallo)
  son **mutuamente excluyentes por construcción** — nunca se apilan castigos. Guard anti-stale: si el
  nivel real del ítem no coincide con el del icono, re-renderiza en vez de actuar.
- Feedback: `enchant-success` / `enchant-fail` / `enchant-fail-refund` / `enchant-downgrade(-lost)` /
  `cursed` (ya no existe `enchant-partial`: no hay ascenso multi-peldaño). Audit
  `ENCHANT`/`ENCHANT-X`/`ENCHANT-DOWN`/`ENCHANT-CURSE` con `start -> end`.
- **NO testeable con MockBukkit** (lee rareza vía EcoEnchants); fórmulas y barras sí están cubiertas.

### Maldiciones: tirada al encantar + curación en yunque — `curse-chance` / `curse-removal`
Sistema de riesgo/sumidero alrededor de las maldiciones (`type: curse`), default **ON**.
- **Tirada (mesa), SIN prevención, TRANSPARENTE y escalada por rareza**: al encantar con **ÉXITO**,
  `EnchantingGUI.maybeApplyCurse` tira el % de la rareza (`curse-chance.by-rarity`, ascendente:
  `comun 0.5 / raro 1.5 / epico 3 / legendario 5 / divino 8` — a mayor rareza, mayor riesgo; divino ya
  NO está a 0 porque ya no se paga con moneda premium) y, si sale, aplica una **maldición aleatoria**
  que targetee el ítem (`EcoEnchantsHook.randomApplicableCurse`, excluye las ya presentes y las
  vetadas; cachea la lista de curses). El **% exacto se muestra en el icono** ANTES de clicar
  (`enchant-icons.lore-curse`, `EnchantingLogic.formatPercent`). **No hay forma de prevenirla**
  (es un gamble inevitable; NO existe sello antimaldiciones). **Veto por config**
  `curse-chance.excluded` (por key/path): **ambas vanilla vetadas** por defecto — `vanishing_curse` (el
  server usa keepinventory → no pierdes ítems al morir) y `binding_curse` (rompería el juego: con
  keepinventory no se suelta al morir y no podrías meterla en el yunque para purificar → armadura pegada
  para siempre). El pool de la tirada son las maldiciones custom (`enchants/maldiciones/`). La tirada va
  **por intento exitoso** (= por nivel ganado, con la progresión secuencial). Feedback: sonido/partícula
  de fallo + actionbar `enchanting.cursed` (con hint de cura), audit `ENCHANT-CURSE`. Solo en éxito (un
  fallo ya te cuesta el coste y arriesga downgrade — los castigos nunca se apilan).
- **Curación (yunque, Sello Purificador) = única salida**: `anvil.curse-removal` (default ON). Objeto
  maldito a la izq + Sello Purificador (`seal-ids`, item MythicMobs) a la der → "Forjar" quita **TODAS**
  las maldiciones y consume el sello. **GRATIS** (el coste es el propio item, que se vende caro).
  `AnvilGUI.isPurifierSacrifice/showPurifierPreview/attemptPurify` (modo aparte, NO toca el `AnvilLogic`
  puro/testeado). Si el objeto no tiene maldiciones → resultado vacío (no gasta nada).
- `EcoEnchantsHook.isCurse(ench)` = `type == curse`. `/se give` sugiere el Sello Purificador. NO testeable
  con MockBukkit (usa eco). Maldiciones siguen fuera de la mesa de oferta (`#curses` blacklist); solo
  entran por esta tirada.
- **Orden de categorías en la mesa**: `EnchantingLogic.CATEGORY_ORDER`
  (comun→raro→epico→legendario→divino→spell→curse; curse no aparece por blacklist). Tipos no listados
  caen al final, luego alfabético. (Antes ordenaba alfabético = desordenado.)

### Comandos (`command/SuperEnchanterCommand.java`)
`/superenchanter` (alias `/se`), Brigadier moderno (Lifecycle API), árbol en
`SuperEnchanterCommand.build()` (el main solo lo registra). Subcomandos:
- `reload` — recarga config+mensajes, refresca cooldown/crash-store. Permiso `superenchanter.reload`.
- `give <id> [jugador]` — da un item de **MythicMobs** configurado (librería o Sello Purificador);
  sugerencias = `enchanted-bookshelves` ∪ `anvil.curse-removal.seal-ids`. `superenchanter.admin`.
- `book <ench> <nivel> [jugador]` — da un libro encantado. El encantamiento se teclea por **path**
  (`sharpness`): Brigadier `word()` **no acepta `:`**, así que se resuelve por ruta (prioriza
  namespace `minecraft`). `superenchanter.admin`.
- `bookshelf` — inspecciona las marcas de librería del **chunk actual** (pos→id→poder). Solo jugador.
- `audit [jugador]` — para un jugador abre el **GUI paginado** (`gui/audit/AuditGUI`): un icono por
  operación (material del ítem resultante + glow si tenía encantamientos), **hover = ítems, nombres,
  id MM y encantamientos** + fecha/lugar/coste; botones prev/info/next/cerrar (slots 48/49/50/53,
  45/página). Lee `AuditLog.readRecent(filtro)` (newest-first, cap `MAX_LOAD`). Para la **consola**
  vuelca texto (`AuditLog.formatConsole`). El registro es estructurado (`audit.jsonl`), NO texto plano.
  `superenchanter.admin`.

Textos en `messages.yml → command.*`. Permiso admin `superenchanter.admin` (default op).

### Config
- Auto-merge: `ConfigUpdater` añade claves nuevas que falten (sin pisar valores del usuario),
  conserva comentarios. `config-version: 16`. Corre en cada reload. ⚠️ El rediseño hardcore
  **rebalanceó valores existentes** (`success-chance.by-rarity`, `curse-chance.*`, y TODAS las curvas
  de coste al pasar a PUNTOS de XP — mesa/yunque/transfer) que el auto-merge NO pisa, y dejó claves
  muertas (boosters, reagents, rarity-cost-type con divino) → **regenerar `config.yml`** en el server
  (borrar y dejar que se regenere) para que aplique el rebalanceo. ⚠️ Si los importes viejos
  (niveles, p.ej. `max-xp-cost: 18`) se quedan con el cobro por puntos, la mesa sale casi GRATIS.
- **`general.gui-disabled-worlds`** (lista de mundos): ahí el yunque/mesa/grindstone quedan
  **vanilla** (no se interceptan). Lo respetan `BlockInteractListener` (no abre la GUI) **y**
  `VanillaBlockListener` (no cancela el inventario vanilla ni los eventos de encantar) →
  `PluginConfig.isGuiWorldDisabled`. Útil para hub/creativo.
- **`audit-log.max-file-kb`** (default 2048): tamaño al que `audit.jsonl` rota a `.1` (anti-crecimiento
  ilimitado); 0 = sin rotación.
- **`enchanting.rarity-cost-type`** (mapa rareza→moneda): override del `cost-type` global por rareza
  (las que no aparezcan heredan el global). Se usa para que **Divino se cobre en PLAYER_POINTS**
  (premium) mientras el resto sigue en XP. Lo resuelve `PluginConfig.getEnchantingCostType(rarityId)`
  (fallback al global) y lo aplica `EnchantingLogic.getEnchantmentLevels`.
- **`anvil.cost-overrides`**: match types `MATERIAL`, `CUSTOM_MODEL_DATA`, `NAMESPACE`, `PDC_KEY`
  (alias `PDC`). `PDC_KEY` con `value` compara el String del PDC (p.ej. el `type` de MythicMobs);
  sin `value`, basta presencia de la clave. (Antes la doc decía `PDC_KEY` pero el código solo
  aceptaba `PDC` y no comparaba el valor → bug arreglado.)
- ⚠️ El auto-merge NO cambia valores existentes; si se rebalancean DEFAULTS (p.ej. multiplicadores),
  hay que regenerar `config.yml` para que apliquen.

---

## Decisiones clave / gotchas
- **🚫 NADA de resource packs (filosofía del proyecto).** El autor RECHAZA los resource packs:
  todo debe lograrse con **vanilla puro explotado al máximo** (bookshelf real en vez de bloque
  reskineado, chiseled sellada, glow con LUCK, MiniMessage, etc.). NO propongas ni implementes
  CustomModelData para "reskinear", texturas custom, ni features que dependan de un pack. Si algo
  "necesita" un pack, busca el truco vanilla equivalente.
- **PacketEvents eliminado**: su interceptor solo hacía `updateInventory()` (redundante) y no podía
  cancelar sin romper los botones (cancelar CLICK_WINDOW mata el InventoryClickEvent). Anti-dupe
  vive 100% en la capa Bukkit.
- **EcoEnchants gestiona los max levels** vía el registry (`Enchantment.getMaxLevel()` ya devuelve
  el max de EcoEnchants). NO añadir un sistema de "max configurable" paralelo; el
  `enchantment-overrides.max-level` solo CAPA por debajo (nunca sube), y lo respetan tanto la mesa
  (`getEnchantmentLevels`) como el gate del yunque (`AnvilGUI.cappedMax`).
- **Sistema de rarezas/tipos DarkMines + guía de encantamientos.** El catálogo custom de EcoEnchants
  (carpeta canónica `C:\Users\Knopp\Desktop\Ecoenchants_Old\`) usa **5 rarezas** español
  (`comun/raro/epico/legendario/divino`, todas con chances 0 = failsafe) + **7 tipos** (las 5 + `curse`
  + `spell`). ⚠️ El id interno del tipo de hechizos sigue siendo `spell` (lo usan `types.yml`,
  el `type-order` del display y el filtro de grupo `id: spell`), pero su **nombre visible es
  "Hechizos"** en TODO lo que ve el jugador (categoría de la mesa custom vía `messages.yml →
  category-names.spell`, y el grupo de la GUI de eco en `EcoEnchants/config.yml`) — deliberado para
  no confundirlo con las "habilidades" de `/skills`. Los hechizos llevan `mana_cost` en sus effects
  (consumen el maná que da Magia). **El color lo da el `type` (no la rareza)**: 5 tipos espejan las rarezas, así
  *categoría = rareza = color = escalado*. La mesa custom agrupa por `type` y escala por `rarity`.
  Divino = premium (`rarity-cost-type` PLAYER_POINTS + se vende como libros). Maldiciones fuera de la
  mesa (`#curses` en blacklist). **Toda la mecánica libreforge (effects/triggers/conditions/filters/
  mutators), el catálogo de efectos verificado, el checklist de revisión y las plantillas están en
  [`docs/GUIA_ENCANTAMIENTOS.md`](docs/GUIA_ENCANTAMIENTOS.md)** — léela antes de tocar encantamientos.
  📚 **Mirror local de toda la doc de Auxilor en `docs/auxilor/`** (~911 .md: effects/conditions/
  filters/mutators/triggers, ecoenchants, ecoskills, actions, lookup-systems) — consúltalo con
  Grep/Read en disco antes de ir al URL online.
  EcoSkills es dependencia de runtime de muchos ymls (gates `has_skill_level`, `add_stat`,
  `skill_xp_multiplier`); **solo 4 skills**: fishing/mining/combat/farming (magia = 5ª futura, NO
  existe `explorer`); no afecta al código del plugin.
  - **⚠️ Gotcha display:** en el `config.yml` de EcoEnchants, `display.sort.rarity: true` SOLO muestra
    las rarezas listadas en `display.sort.rarity-order`. Si quedan ids viejos ahí, los encantamientos
    se aplican pero NO se ven en el ítem. `rarity-order` debe tener los ids nuevos
    (`divino/legendario/epico/raro/comun`). Cambios de `display` requieren REINICIAR el server.
  - **⚠️ Gotcha vanilla:** los encantamientos vanilla viven en `vanillaenchants.yml` (NO en
    `enchants/`), y **también** llevan `type`/`rarity` que hay que migrar a los ids nuevos. Si quedan
    los viejos (`type: normal`, `rarity: vanilla/rare/uncommon`), EcoEnchants los resuelve a un default
    (categoría→primer tipo `comun`, rareza→`divino`) → aparecen en la mesa con el **tag de rareza
    equivocado** (p.ej. Irrompibilidad como "divino"). Ya migrados a `comun` (curses `type: curse`);
    rebalancea por encantamiento si quieres otra rareza.
- **Sonidos/partículas configurables** (`config/SoundEffect`, `ParticleEffect`): los sonidos son
  objetos `{ key, volume, pitch }` (o clave suelta) — sección `sounds`, incl. `button-click` para
  navegación. Las partículas (`enchant-success`, `library-ambient` con `period-ticks`) viven en la
  sección `particles` con `{ enabled, type, count, offset, speed }`. Nada de esto va ya hardcodeado.
- **Sonidos**: claves namespaced (`block.anvil.use`), NO nombres de enum (bug antiguo: todos caían
  a villager.no).
- **`/se reload` limpia las caches de `EcoEnchantsHook`** (`clearCaches()`: wrap/baseName/requirement).
  Necesario porque esas caches viven para siempre; sin esto, editar ymls de encantamientos
  (nombres/descripciones/`not-met-lines`) + `/ecoenchants reload` no se reflejaba hasta reiniciar.
- **Self-check de integración eco al arrancar** (`EcoEnchantsHook.logIntegrationSelfCheck`, salta bajo
  MockBukkit): recorre el registro y logea cuántos encantamientos tienen config legible vía reflexión
  + líneas de requisito; si NINGUNO da config legible → WARN ruidoso. Convierte fallos silenciosos
  (como el `instanceof` roto que ocultó los requisitos) en visibles. También calienta la requirementCache.
- **Hot-swap de jars = no.** Reiniciar siempre.
- DataComponent API usada en EnchantmentHelper/ItemBuilder (CMD via componente). Es `@Experimental`.
- **`SuperEnchanterPlugin` NO es `final`**: MockBukkit le hace proxy (subclase ByteBuddy) al
  cargarlo en tests; `final` rompe `MockBukkit.load(...)`.
- **Salvavidas de arranque**: EcoEnchants es **softdepend** (no depend) a propósito → el plugin
  SIEMPRE carga y decide él. `onEnable` va envuelto en try/catch; si falta EcoEnchants o algo peta
  → `criticalFailure()` apaga el SERVER (`getServer().shutdown()`) o solo desactiva el plugin según
  `fail-safe.shutdown-on-critical-error` (default true). ⚠️ El chequeo se **salta bajo MockBukkit**
  (`isMockEnvironment()` mira si el server class name contiene "mock") — sin esa guarda, los tests
  apagarían el "server" mock. NO añadas checks críticos en `onEnable` sin esa guarda.
- **Hiperbloqueo vanilla**: `VanillaBlockListener` cancela el `InventoryOpenEvent` de
  ANVIL/ENCHANTING/GRINDSTONE (venga de donde venga) + `BlockInteractListener` cancela el interact;
  además cancela `PrepareItemEnchantEvent`/`EnchantItemEvent` como belt (nuestra mesa no los dispara,
  así que solo bloquea vanilla). Efecto colateral: GUIs de input tipo anvil de OTROS plugins también
  se bloquean (holder no es `AbstractCustomGUI`).
- **Sonidos por acción**: transferir/extraer ya NO comparten sonido con encantar
  (`transfer-success`/`extract-success`); abrir/cerrar usa barrel, no cofre. **`enchant-fail`**
  (sonido + partícula `LARGE_SMOKE`) para el fallo del encantamiento probabilístico.

---

## Pendientes / cobertura de tests
- **Orbe gacha** (item suelto, clic derecho → encantamiento aleatorio de una rareza): NO implementado.
- **Pre-índice** de encantamientos por categoría al arrancar (solo si hay lag con muchísimos EcoEnchants).
- **Tests** (JUnit 5 + MockBukkit `org.mockbukkit:mockbukkit-v26.1.2`; paper-api declarada como
  `testImplementation` aparte porque el `compileOnly` del main no se hereda): **cubierto** = matemática
  pura (`AnvilFormulas`, `EnchantFormulas` con `geometricCost`/`filledSegments`/`progressBar`,
  `XpMath`, `Cost`/`CostType`,
  `BookshelfScanner.lineCells`, `EnchantingLogic.classifyBlock`) + MockBukkit (`AnvilLogic.calculateResult`
  con el `AnvilEnchantGate`, `BookshelfScanner.scan`, `CostService`). ⚠️ **NO testeable con MockBukkit**
  todo lo que llama a EcoEnchants/EcoSkills (`analyze()`, `nextStep`, `TransferLogic.computeOffers`,
  Magia, maldiciones, downgrade end-to-end): `com.willfp.*` es compileOnly → `NoClassDefFoundError` en
  runtime de test; haría falta un harness con eco REAL (servidor, no Mock). Es el hueco grande de
  cobertura, sin resolver.
- **Diseño descartado (rediseño hardcore 2026-06)**: el coste ACUMULATIVO con salto de niveles
  (`cumulativeCost`, saltar a V = suma I→V) fue REEMPLAZADO por progresión secuencial obligatoria +
  downgrade; los sellos de rareza/garantía y los reactivos materiales fueron eliminados. Las librerías
  encantadas siguen siendo la progresión "de infraestructura" (gate de poder por rareza).
