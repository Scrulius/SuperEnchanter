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
  `CostService` (canAfford/deduct/balanceText + `effectiveCost`/`discountMultiplier`, multi-moneda
  compartido por yunque, mesa y transferencia).
- `util/` — ItemBuilder, MiniMessageUtil, EnchantmentHelper, CooldownManager,
  PendingItemStore, ConfigUpdater, EnchantedBookshelfManager, `AuditLog` (rastro de operaciones
  a `audit.log`; línea construida en main thread, I/O de archivo async; gateado por `audit-log.enabled`).

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

### Mesa de encantamientos (EnchantingGUI) — 3 niveles
1. **Categorías** = tipos de EcoEnchants (`EcoEnchantsHook.getTypeId`).
2. **Encantamientos** de la categoría.
3. **Niveles** (coste/poder/reactivo).
- Botón cerrar hace de "atrás" (sube un nivel).
- **Bloqueados se muestran con motivo**: `EnchantingLogic.analyze()` escanea el registry UNA vez
  por ítem (cacheado) → `AnalyzedEnchant` con `BlockReason` (NONE/MAXED/CONFLICT/MISSING_REQUIRED/
  TYPE_LIMIT). Bloqueados = gris con el porqué; no se pueden abrir (eso también evita saltarse el
  chequeo de conflicto). Usa el modelo real de EcoEnchants (targets/conflicts/required/typeLimit).
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
- **Coste por rareza (acumulado)**: el coste POR PASO es
  `paso(nivel) = min(max-xp-cost, multRareza*(base + mult*nivel^cost-exponent))` (el cap capa CADA
  paso, no la suma). El importe que se cobra al saltar a un nivel destino es la **suma de los pasos**
  desde el nivel actual hasta el destino (`EnchantFormulas.cumulativeCost`, testeada): ir directo a
  V cuesta lo mismo que forjar I→II→III→IV→V a mano → la curva es un sumidero real, no un peaje del
  último nivel. Precomputado en `getEnchantmentLevels` (array `perLevelCost`). Se cobra en la moneda
  `enchanting.cost-type` (XP por defecto; también VAULT/PLAYER_POINTS) vía `CostService` — igual que
  el yunque. Mensajes de coste genéricos (`{cost}`/`{balance}` ya formateados por moneda).
- **Poder por rareza**: `min(maxPower, floor + nivel*step)` por rareza (`enchanting.rarity-power`).
  El poder gatea la MAGNITUD, no la fracción de nivel.
- **Reactivo material** por rareza (item+amount+CMD opcional). ⚠️ Con la tirada POR PASO el reactivo
  se consume EN CADA peldaño, así que `reagent-scales-with-level` va en **false** (si no se
  multiplicaría dos veces) y los importes son bajos (lapis/amatista; se quitaron netherite_scrap/
  nether_star que a ×5 peldaños eran inviables).
- **Overrides por encantamiento** (`enchanting.enchantment-overrides`, por key completa o ruta):
  `max-level` (capa niveles), `xp-cost` (coste fijo), `required-power` (poder fijo),
  `cost-multiplier`. Aplicados en `getEnchantmentLevels` (Ola 3).
- **Iconos/nombres de categoría configurables**: `enchanting.category-icons` (material por tipo +
  `default`); `category-names`/`rarity-names` en **messages.yml** (localización, fallback title-case).
- **Single-level sin romano**: un encantamiento con maxLevel 1 se muestra "Vitalidad", no
  "Vitalidad I" (`LevelEnchantmentOffer.levelSuffix()` gateado por maxLevel>1). Los iconos muestran
  además **rareza** (`lore-rarity`) y progreso de nivel (`lore-level`).

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
- **Coste** vía `EnchantFormulas.xpCostForLevel` (POR encantamiento; el botón cobra la **suma** de los
  seleccionados, `TransferGUI.totalCost`). Sección `transfer` (cost-type, base/level-mult/exponent/
  max-cost, `use-rarity-multiplier`, `require-same-material`). **Rebalanceado a propósito MÁS barato que
  la mesa** (`base 2 / level-mult 3 / cap 20`): mover/extraer recupera algo ya creado (y destruye el
  donante), no debe costar más que encantar de cero.
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
  (tematizada como "Magia"). Su XP la damos NOSOTROS por API (`giveSkillXp`) — la mesa custom NO
  dispara el encantar vanilla del que viviría esa skill (y `VanillaBlockListener` bloquea la mesa
  vanilla → cero doble-conteo). **Lado A (server, HECHO)**: `EcoSkills/skills/enchanting.yml`
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
  - **C5 gating** (`gating.enabled`, default **OFF**): exige nivel mínimo de Magia por rareza
    (`canEnchant`/`requiredLevel`); bloqueado → actionbar `enchanting.magia-locked`.
- **XP por operación** (`grantXp`, devuelve la XP dada): `niveles_ganados × xp(rareza)` + una
  fracción (`xp-fail-fraction`, 0.25) si un peldaño falló ("aprendes del error"). Solo si se cobró
  algo. **Feedback**: el action bar de encantar (success/partial/fail/cursed) anexa el sufijo
  `enchanting.magia-xp-suffix` (`{magia}` → "+N ✦ Magia") cuando se ganó XP.
- **Reembolso Arcano** (sub-habilidad, `enchanting.magia.refund`): al FALLAR un peldaño, prob.
  `min(max-percent, nivel*per-level)` (default 0.6/nivel, tope 30% a nivel 50) de **recuperar el
  coste** de ese peldaño — `CostService.refund` (XP: `setLevel+`; Vault: `deposit`; PP: `give`).
  Suaviza el sumidero del `success-chance`. Feedback: `enchant-fail-refund` (primer peldaño) o
  sufijo `refund-suffix` (`{refund}` en el parcial).
- **Visibilidad de los bonus**: el icono de nivel muestra `enchant-icons.magia-line`
  (`🔮 Magia N · +X% éxito · -Y% coste · ♻Z%`) vía `EnchantingLogic.appendMagiaLore`, y el maná en
  el panel de `/skills` (config de EcoSkills). ⚠️ **Fix**: `appendChanceLore` ahora suma el
  `successBonus` de Magia al % mostrado — antes el icono enseñaba un % MENOR que el que se tiraba de
  verdad en `handleLevelClick`. Helpers de display: `MagiaService.discountPercent/refundChance/manaBonus`.
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

### Encantamiento probabilístico + potenciadores (mesa) — `success-chance`
**Activado por defecto** (`enchanting.success-chance.enabled: true`, norma plugin-privado; `false` =
kill-switch que vuelve al 100% garantizado clásico).
Con él, cada intento de encantar tiene una **probabilidad de éxito por rareza**; un **fallo consume
TODO igual** (coste + reactivo) — ese es el riesgo/sumidero.
- **Curva**: `prob = clamp(base(rareza) + %potenciador, 0..100)` (`EnchantFormulas.effectiveChance`,
  pura/testeada). Base por rareza en `success-chance.by-rarity` (+ `default`).
- **Sellos (potenciadores)**: items de **MythicMobs** que se meten en un **slot dedicado** en la mesa
  (`SLOT_BOOSTER=37`, etiqueta en 36, SOLO visible si la feature está ON; es input slot → herencia
  anti-dupe/crash gratis vía `getInputSlots()`). Modelo **por rareza** (`PluginConfig.Booster` =
  `{rarity, percent}`): un sello se **ata a una rareza** y solo aporta su `percent` (y solo se consume)
  cuando el encantamiento es de ESA rareza; con otra rareza da 0 y NO se gasta. `percent 100` sobre su
  rareza = **garantía** (un "Sello Raro" hace que un encantamiento raro entre al 100%). `rarity: '*'`
  (o `any`/`all`/omitido, o forma corta `id: 25`) = sello **universal** (cualquier rareza). Config en
  `success-chance.boosters: { id → {rarity,percent} }`. `getBoosterPercent(mmId, enchantRarity)`
  resuelve el aporte según la rareza del encantamiento seleccionado. Se consumen en CADA intento por
  defecto (`boosters-consumed-on-success-only` lo cambia a solo-éxito).
- **Tirada POR PASO** (`EnchantingGUI.handleLevelClick`, rediseñado): clicar un nivel destino **sube
  peldaño a peldaño** desde el nivel actual; cada peldaño cobra **solo su `stepCost`** + reactivo
  (consumidos aun fallando) y tira `ThreadLocalRandom`. Un fallo **detiene el ascenso CONSERVANDO los
  niveles ya logrados** (checkpoints) → nunca pierdes más de un peldaño, y reintentar solo paga lo que
  falta (coste acumulativo salta lo poseído). Un **sello** (booster) cubre TODO el ascenso y se consume
  UNA vez. `LevelEnchantmentOffer.stepCost` = coste de un peldaño (vs `cost` = acumulado mostrado).
  Feedback: `enchant-success` (completo) / `enchant-partial` (parado a medias) / `enchant-fail` (primer
  peldaño falla) / `cursed`. Audit `ENCHANT`/`ENCHANT-X`/`ENCHANT-CURSE` con `start->reached`.
- **NO testeable con MockBukkit** (lee rareza vía EcoEnchants); solo la fórmula está cubierta.

### Maldiciones: tirada al encantar + curación en yunque — `curse-chance` / `curse-removal`
Sistema de riesgo/sumidero alrededor de las maldiciones (`type: curse`), default **ON**.
- **Tirada (mesa), SIN prevención (decisión de diseño: Opción B)**: al encantar con **ÉXITO**,
  `EnchantingGUI.maybeApplyCurse` tira un % bajo **por nivel ganado** (`enchanting.curse-chance.
  base-percent`, default **0.4** → ~2% al subir un ítem a V, ya que la tirada es por peldaño;
  override `by-rarity`, `divino: 0`) y, si sale, aplica una **maldición aleatoria** que targetee el ítem
  (`EcoEnchantsHook.randomApplicableCurse`, excluye las ya presentes y las vetadas; cachea la lista de
  curses). **No hay forma de prevenirla** (es un gamble inevitable) — los sellos de rareza NO inmunizan
  y NO existe sello antimaldiciones (se descartó: prevenir mataba la emoción). **Veto por config**
  `curse-chance.excluded` (por key/path): **ambas vanilla vetadas** por defecto — `vanishing_curse` (el
  server usa keepinventory → no pierdes ítems al morir) y `binding_curse` (rompería el juego: con
  keepinventory no se suelta al morir y no podrías meterla en el yunque para purificar → armadura pegada
  para siempre). El pool de la tirada son las maldiciones custom (`enchants/maldiciones/`). La tirada va
  **por NIVEL ganado** (con tirada por paso, subir de golpe o peldaño a peldaño exponen igual; máx UNA
  maldición por operación). Feedback: sonido/partícula de fallo + actionbar `enchanting.cursed` (con hint
  de cura), audit `ENCHANT-CURSE`. Solo en éxito (un fallo ya te cuesta todo).
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
- `give <id> [jugador]` — da un item de **MythicMobs** configurado (librería o potenciador);
  sugerencias = `enchanted-bookshelves` ∪ `success-chance.boosters`. `superenchanter.admin`.
- `book <ench> <nivel> [jugador]` — da un libro encantado. El encantamiento se teclea por **path**
  (`sharpness`): Brigadier `word()` **no acepta `:`**, así que se resuelve por ruta (prioriza
  namespace `minecraft`). `superenchanter.admin`.
- `bookshelf` — inspecciona las marcas de librería del **chunk actual** (pos→id→poder). Solo jugador.
- `audit [líneas] [jugador]` — vuelca el final de `audit.log` (`AuditLog.tail`, filtro por substring).
  Las líneas se mandan como `Component.text` SIN parsear MiniMessage (un `<` del log no es un tag).
  `superenchanter.admin`.

Textos en `messages.yml → command.*`. Permiso admin `superenchanter.admin` (default op).

### Config
- Auto-merge: `ConfigUpdater` añade claves nuevas que falten (sin pisar valores del usuario),
  conserva comentarios. `config-version: 8`. Corre en cada reload.
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
- **`success-chance` default ON** (norma "plugin privado": es la experiencia base de DarkMines). El
  slot de potenciador/sello SOLO existe con la feature ON (`getInputSlots()` lo añade
  condicionalmente); con OFF (`false`, kill-switch temporal), slot 37 es relleno decorativo y se
  vuelve al 100% garantizado.

---

## Ideas pendientes (no hechas, para retomar)
- ~~CostResolver compartido yunque+mesa~~ HECHO: paquete `economy/` (`CostService`); la mesa cobra
  en `enchanting.cost-type` (XP/VAULT/PLAYER_POINTS).
- ~~Progresión nivel-a-nivel~~ DESCARTADO: se mantienen las 3 tiers de la mesa. En su lugar el coste
  es ACUMULATIVO (`EnchantFormulas.cumulativeCost`) — saltar a V cuesta la suma de I→V — que es el
  sumidero real sin el tedio de subir de uno en uno. Las librerías encantadas ya son la "progresión"
  de verdad (gate de poder por rareza).
- Tests JUnit (`./gradlew.bat test`, JUnit 5): HECHO. Matemática pura (`AnvilFormulas`,
  `EnchantFormulas` incl. `cumulativeCost`, `Cost`/`CostType`) + **MockBukkit**
  (`org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.1`, paper-api declarada aparte como
  `testImplementation` porque el `compileOnly` del main no se hereda) para `AnvilLogic.calculateResult`
  (merge real con DataComponents + conflictos vanilla + el `AnvilEnchantGate` inyectado, probado con
  gates lambda: rechazo→conflicto, cap de nivel, `ALLOW_ALL`=vanilla), `BookshelfScanner.scan` (suma
  de poder + override de librería vía PDC de chunk) y `CostService` (XP real; Vault/PP ausentes → no
  pagable). La **precedencia de `BlockReason`** está extraída a `EnchantingLogic.classifyBlock`
  (puro, sin Bukkit/eco) y cubierta por `EnchantingLogicTest` (9 casos: maxed/upgrade/precedencia
  required>conflict>type-limit). PENDIENTE: el resto de `analyze()` (scan + resolución de
  targets/conflicts vía eco) y `TransferLogic.computeOffers` — NO testeables con MockBukkit porque
  llaman a EcoEnchants (`com.willfp.*` es compileOnly → `NoClassDefFoundError` en runtime de test;
  haría falta eco real).
- ~~Comando debug `/se bookshelf`~~ HECHO (+ `/se give`, `/se book`, `/se audit`) en
  `command/SuperEnchanterCommand`.
- ~~Encantamiento probabilístico + sellos de garantía por rareza (MythicMobs)~~ HECHO
  (`enchanting.success-chance`, default ON). Pendiente opcional: **orbe gacha** suelto (click
  derecho → encantamiento aleatorio de una rareza), no implementado.
- ~~Obtención/economía de maldiciones~~ HECHO: tirada de maldición al encantar (sin prevención) +
  Sello Purificador (yunque) como única cura — ver `curse-chance`/`curse-removal`.
- ~~No hay encantamientos "gratis" en loot natural~~ HECHO: `LootControlListener` (`loot-control`).
- Air-gap del escáner es heurístico (no line-of-sight real).
- Pre-índice de encantamientos por categoría al arrancar (si hay lag con muchísimos EcoEnchants).
