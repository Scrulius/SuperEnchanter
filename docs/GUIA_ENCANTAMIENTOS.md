# Guía de encantamientos — DarkMines (EcoEnchants + libreforge + SuperEnchanter)

> Guía para **crear, revisar y entender** los encantamientos custom del server.
> Pensada para que cualquier Claude (o el autor) trabaje sobre el catálogo sin
> romper nada. Léela entera antes de tocar un `.yml` de encantamiento.

---

## 1. Las piezas y cómo encajan

| Pieza | Qué hace | Dónde |
|---|---|---|
| **EcoEnchants** | Registra los encantamientos custom como `Enchantment` reales. Define rarezas (`rarity.yml`) y tipos/categorías (`types.yml`). | Carpeta del plugin EcoEnchants en el server. Catálogo canónico del autor: `C:\Users\Knopp\Desktop\Ecoenchants_Old\`. |
| **libreforge** | El motor de efectos que hay debajo de EcoEnchants (y de EcoSkills, EcoItems…). Es quien interpreta `effects/triggers/conditions/filters/mutators`. | Interno; se configura **dentro** de cada `.yml` de encantamiento. |
| **EcoSkills** | Sistema de habilidades. Solo hay **4**: `fishing` (Pesca), `mining` (Minería), `combat` (Combate), `farming` (Agricultura). Muchos encantamientos lo usan (gates `has_skill_level`, `add_stat`, `skill_xp_multiplier`, `%ecoskills_*%`). **Debe estar instalado** (los ymls lo declaran como `dependencies`). | Plugin aparte. |
| **SuperEnchanter** | Reemplaza yunque/mesa/grindstone con GUIs custom. **NO define encantamientos**: los lee del registro de EcoEnchants. | Este repo. |

**Relación clave SuperEnchanter ↔ EcoEnchants:**
- La **categoría** de la mesa custom = el **`type`** del encantamiento (`EcoEnchantsHook.getTypeId`).
- El **color** del nombre = el **`format`** de ese `type` (`types.yml`). *La rareza NO da color.*
- El **escalado** (coste / poder de estantería / probabilidad de éxito / reactivo) = la **`rarity`**
  (`EcoEnchantsHook.getRarityId`), vía las secciones `enchanting.*` de `config.yml`.

Por diseño, **5 tipos espejan las 5 rarezas** (mismo id) → *categoría = rareza = color = escalado*.
Las maldiciones (`type: curse`) y los hechizos (`type: spell`) son **categorías propias** pero
**igualmente llevan una de las 5 rarezas** para el escalado.

---

## 2. Rarezas y tipos (las categorías)

### Rarezas (`rarity.yml`) — 5, todas con probabilidades a **0**
`comun, raro, epico, legendario, divino`. **Todas** las chances (tabla/aldeano/loot) están a 0: es un
**failsafe** — la obtención pasa SIEMPRE por la mesa custom de SuperEnchanter; si esa mesa no cargara,
nadie sacaría encantamientos por vías vanilla. (Refuérzalo con `enchantable/tradeable/discoverable:
false` en cada enchant — ver §4.)

### Tipos/categorías (`types.yml`) — 7
| `type` | Color (format) | Límite por ítem | Categoría en la mesa |
|---|---|---|---|
| `comun` | gris `<#B0B6BE>` | ∞ | Común |
| `raro` | azul | 4 | Raro |
| `epico` | morado | 2 | Épico |
| `legendario` | oro | 1 | Legendario |
| `divino` | celestial (premium) | 1 | Divino |
| `curse` | rojo | ∞ | Maldición (`no-grindstone`) |
| `spell` | cian | 1 | Habilidad (hechizos `alt_click`) |

El `limit` es **balance intencional**: solo 1 legendario / 2 épicos / 4 raros por ítem. Lo respeta
EcoEnchants y SuperEnchanter (bloqueo `TYPE_LIMIT`).

### Divino = premium
- En SuperEnchanter, `enchanting.rarity-cost-type.divino: PLAYER_POINTS` → en la mesa se cobra con la
  **moneda premium** (el resto en XP). El resto de divinos también se pueden vender como **libros**
  (con `/se book <ench> <nivel>`) por la moneda premium y aplicarlos en el yunque.
- `success-chance.by-rarity.divino: 100` (premium no se pierde por RNG).

### Maldiciones
Quedan **fuera de la mesa** por defecto (`enchanting.disabled-enchantments: ["#curses"]`). La
categoría/color existen por si se quieren ofrecer por otra vía (drops, crates, gacha…).

---

## 3. Dónde tocar en SuperEnchanter al crear un tier/categoría nuevo

Si solo creas encantamientos de rarezas/tipos **ya existentes**, NO tocas SuperEnchanter. Si añades
una **rareza o tipo nuevo**, actualiza en `src/main/resources/`:
- `config.yml → enchanting`: `success-chance.by-rarity`, `rarity-cost-multipliers`, `rarity-power`,
  `rarity-reagents`, `rarity-cost-type` (si premium), `category-icons` (icono del tipo),
  `success-chance.boosters` (sello de la rareza).
- `messages.yml`: `rarity-names.<id>` y `category-names.<id>` (con su color MiniMessage).

---

## 4. Anatomía de un `.yml` de encantamiento

```yaml
display-name: "Robo de vida"          # Nombre visible (sin color; el color lo da el type)
description:                          # Lore. Admite &-codes/MiniMessage y %placeholders%
  - "Cura un &a%placeholder%%&r del daño infligido"
placeholder: "%level% * 10"          # Valor de %placeholder% (expresión matemática)
placeholders:                        # Placeholders extra con nombre (opcional)
  bolts: "ceil(%level% / 2)"         #   → se usan como %bolts% en description/args

type: epico                          # CATEGORÍA + COLOR (id de types.yml)
rarity: epico                        # ESCALADO (id de rarity.yml). Para curse/spell, igual una de las 5.
targets:                             # A qué ítems aplica (ids de targets.yml: sword, bow, pickaxe, helmet…)
  - sword
  - axe
conflicts: []                        # Encantamientos incompatibles (no coexisten)
required: []                         # Encantamientos que deben estar ANTES de poder aplicar este
max-level: 3

# ── Failsafe: nunca se obtiene por vías vanilla; solo por la mesa custom ──
tradeable: false                     # Aldeanos
discoverable: false                  # Loot natural
enchantable: false                   # Mesa de encantamientos VANILLA (la custom lo ofrece igual)

dependencies:                        # Plugins requeridos (si usa EcoSkills, decláralo)
  - EcoSkills

effects:                             # QUÉ HACE (ver §5)
  - id: give_health
    args:
      amount: "2"
      chance: "%level% * 10"
    triggers:
      - melee_attack

conditions: []                       # Requisitos para que el enchant esté activo (ver §5)
```

> **Encoding:** guarda SIEMPRE en **UTF-8** sin romper acentos (`corazón`, no `coraz�n`).

---

## 5. libreforge: effects / triggers / conditions / filters / mutators

Un bloque `effects:` es una lista. Cada efecto tiene:

```yaml
- id: <efecto>            # QUÉ acción ejecuta (§5.2)
  args: { ... }           # parámetros del efecto (admiten %placeholders% y matemáticas)
  triggers: [ ... ]       # CUÁNDO se dispara (§5.1). Obligatorio salvo efectos permanentes.
  filters: { ... }        # restringe SEGÚN el contexto del trigger (§5.4)
  conditions: [ ... ]     # requisitos del JUGADOR para que aplique (§5.3)
  mutators: [ ... ]       # transforman el objetivo/datos antes de ejecutar (§5.5)
  chance: <0-100>         # probabilidad de ejecutar (opcional)
  every: <n>              # ejecuta 1 de cada n veces que se dispara (opcional)
  repeat: { times, delay-ticks }   # repetir N veces con retardo (opcional)
```

### 5.0 Efectos PERMANENTES vs DISPARADOS vs ACTIVOS (hechizos)
- **Disparado** (lo normal): tiene `triggers`. Ej. daño al golpear.
- **Permanente**: SIN `triggers`. Aplica mientras el ítem esté equipado y se cumplan las `conditions`.
  Ej. `add_stat`, `permanent_potion_effect`.
- **Activo / hechizo** (`type: spell`): trigger `alt_click` + `args.cooldown` + `send_cooldown_message`.
  Es un grupo de efectos anidado (ver §5.6).

### 5.1 Triggers más usados
`melee_attack`, `bow_attack`, `trident_attack`, `projectile_hit`, `headshot`, `shield_block`,
`take_damage`, `entity_death`, `mine_block`, `place_block`, `jump`, `toggle_sneak`, `alt_click`
(click derecho con el ítem), `death`, `static_%n%` (cada n ticks: `static_20` = 1s). Cada trigger
expone datos del contexto (la víctima, el bloque, el daño…) accesibles como placeholders.

### 5.2 Catálogo de efectos VERIFICADO en este server (los que ya usamos)
| `id` | Qué hace | args típicos |
|---|---|---|
| `add_damage` | Suma daño **plano** | `damage` |
| `damage_multiplier` | Multiplica el daño (usa para "% más daño") | `multiplier` |
| `crit_multiplier` | Multiplica el daño **de críticos** | `multiplier` |
| `add_stat` | Suma una stat de EcoSkills (permanente) | `stat` (p.ej. `crit_damage`), `amount` |
| `skill_xp_multiplier` | Multiplica XP de habilidad(es) (permanente) | `multiplier`, `skills: [combat]` |
| `give_health` | Cura vida | `amount`, `chance` |
| `bleed` | Sangrado por ticks | `chance`, `damage`, `interval`, `amount` |
| `spawn_mobs` | Invoca mobs | `entity`, `amount`, `health`, `ticks_to_live`, `range`, `speed`, `chance` |
| `victim_speed_multiplier` | Ralentiza/acelera a la víctima | `multiplier`, `duration` |
| `set_freeze_ticks` | Congela (efecto powder-snow) | `ticks`, `chance` |
| `strike_lightning` | Rayos | `amount`, `chance` |
| `mine_vein` | Mina la veta entera | `limit` + `filters.blocks` |
| `transmission` | Teletransporta hacia delante | `distance` |
| `cancel_event` | Cancela el evento del trigger (fallar golpe, salvar de morir…) | `chance` |
| `telekinesis` | Drops/XP directos al inventario | (sin args) |
| `play_sound` | Sonido | `sound`, `pitch`, `volume` |

> **Hay cientos más** (libreforge: `potion_effect`, `permanent_potion_effect`, `ignite`, `give_xp`,
> `run_command`, `send_message`, `spawn_particle`, `apply_velocity`…). Antes de usar uno que NO esté
> en esta tabla, **verifícalo** contra un enchant que ya lo use o la doc, porque un `id` o `arg`
> inexistente **hace que el enchant no cargue**.
>
> 📚 **Mirror local de TODA la doc de Auxilor** en `docs/auxilor/` (descargado, ~911 archivos):
> `effects/` (all-effects/effects/conditions/filters/mutators/triggers), `ecoenchants/`, `ecoskills/`,
> `actions/`, `lookup-systems/`, `all-plugins/`. Consúltalo en disco (Grep/Read) en vez de ir al URL
> online — p.ej. `docs/auxilor/effects/all-effects/<id>.md` para un efecto concreto, o
> `docs/auxilor/effects/all-triggers.md` para la lista de triggers.

### 5.3 Conditions (requisitos del jugador)
Se evalúan sobre el JUGADOR; si no se cumplen, el efecto no aplica (y se puede tachar el nombre con
`not-met-lines`). La más usada aquí:
```yaml
conditions:
  - id: has_skill_level          # EcoSkills
    args:
      skill: combat              # id de la habilidad
      level: 25
      not-met-lines:
        - "&#e534ebRobo &#B9BCBErequiere &#D1161BCombate &#871518XXV"
```
Otras: `is_sneaking` (`args.is_sneaking: true`), `above_y`, `below_y`, `in_water`, `has_permission`…

### 5.4 Filters (restringen según el contexto del trigger)
Van **dentro del efecto**, no en conditions. Ejemplos reales:
```yaml
filters:
  entities: [cow, sheep, pig, villager]   # solo contra estos mobs (trigger de combate)
filters:
  blocks: [coal_ore, iron_ore, ...]       # solo estos bloques (trigger de minado)
filters:
  is_boss: true                           # solo contra jefes (MythicMobs)
```

### 5.5 Mutators (transforman el objetivo antes de ejecutar)
El más usado: `player_as_victim` — hace que el efecto recaiga sobre el JUGADOR en vez del objetivo
(útil en maldiciones: invocar mobs que te atacan a TI).
```yaml
mutators:
  - id: player_as_victim
```

### 5.6 Grupos de efectos anidados (para hechizos / efectos combinados)
Varios efectos que comparten trigger/cooldown se anidan: una entrada de la lista con su propia clave
`effects:` + `args` (cooldown) + `triggers`:
```yaml
effects:
  - effects:
      - id: transmission
        args: { distance: "3 + %level% * 2" }
      - id: play_sound
        args: { sound: entity_enderman_teleport, pitch: 1.2, volume: 1 }
    args:
      cooldown: 90               # segundos
      send_cooldown_message: true
    triggers:
      - alt_click
```

### 5.7 Placeholders y matemáticas
- `%level%` = nivel del encantamiento. `%placeholder%` y `%<nombre>%` (de `placeholders:`).
- Contexto del trigger: `%victim_*%` (vida, etc.), `%trigger_value%` / `%v%` (valor del trigger,
  p.ej. el daño), `%player_*%`, `%location_*%`. EcoSkills: `%ecoskills_<stat>_name%`, etc.
- Matemáticas: `+ - * /`, `ceil()`, `floor()`, paréntesis. Ej: `"1 + 0.1 * %level%"`,
  `"%v% * (0.2 + %level% * 0.1)"`.

---

## 6. EcoSkills (se queda)
Habilidades existentes (**solo 4**): `fishing` (Pesca), `mining` (Minería), `combat` (Combate),
`farming` (Agricultura). **NO existe `explorer`** (se borró). **`magia` es la 5ª, FUTURA** (rework
de EcoSkills pendiente): hoy los enchants que querrían gatear por magia (`thor`, `tiro_teledirigido`,
`brujeria`) apuntan a `combat` con comentario para migrarlos a `magia` cuando exista. Patrones:
- **Gate**: `conditions: - id: has_skill_level` (ver §5.3). Si el jugador no llega al nivel, el
  encantamiento no surte efecto y se tacha.
- **Stats**: `add_stat` (p.ej. `crit_damage`) — permanente.
- **XP boost**: `skill_xp_multiplier` con `skills: [combat]`.
- Declara `dependencies: [EcoSkills]` en esos ymls (EcoEnchants desactiva el enchant si falta).

---

## 7. Checklist de REVISIÓN ("qué estaba mal hecho")
Al revisar un enchant viejo, comprueba y corrige:
1. **`conditions:` rota** — bloque `conditions:` con `args:/skill:/not-met-lines:` **sin `- id:
   has_skill_level`**. Re-añade el `- id:` (la condición estaba muerta).
2. **`type:` inválido** — debe ser uno de los 7 ids de `types.yml`. (Antes había `type: common/epic…`
   con types inexistentes → sin color.)
3. **`rarity:` antigua** (`common/rare/epic/legendary/special/maldicion`) → migrar a
   `comun/raro/epico/legendario/divino`.
4. **Encoding** roto (`coraz�n`, `Resurección`) → UTF-8 correcto + ortografía.
5. **Descripción ≠ efecto** — si dice "%", usa `damage_multiplier`/`crit_multiplier`, no `add_damage`
   plano (y al revés). Ajusta el `placeholder` para que el número mostrado sea el real.
6. **Target incoherente** — el ítem debe casar con el concepto (un bonus de Combate no va en `pickaxe`).
7. **Targets duplicados** / `max-level` raros / flags `tradeable/discoverable/enchantable`
   inconsistentes → failsafe (`false` los tres salvo excepción justificada).
8. **El enchant carga** sin error (copiar al server de pruebas y mirar consola al arrancar).

> Objetivo: **mismo comportamiento que antes, pero con la lógica bien puesta.** Si el viejo estaba
> roto (condición muerta, efecto que no casa con la descripción), arréglalo a lo que claramente se
> pretendía.

---

## 8. Checklist de CREACIÓN de un encantamiento nuevo
1. Crea el `.yml` en `enchants/<categoria>/<id>.yml` (carpeta = comun/raro/epico/legendario/
   habilidad/maldiciones, o divino).
2. Rellena la anatomía (§4): `type` (categoría/color) + `rarity` (escalado) + `targets` + `max-level`
   + flags failsafe.
3. Diseña los `effects` con efectos **verificados** (§5.2) y sus triggers/filters/conditions.
4. Si usa EcoSkills, añade `dependencies: [EcoSkills]` y el gate.
5. Si es rareza/tipo NUEVO, actualiza SuperEnchanter (§3).
6. Prueba carga + en juego (ver §9).

---

## 9. Deploy y gotchas

> **⚠️ GOTCHA CRÍTICO — los encantamientos no se ven en el ítem.** En el `config.yml` de
> EcoEnchants, `display.sort.rarity: true` hace que **solo se muestren las rarezas listadas en
> `display.sort.rarity-order`**. Si ahí quedan ids viejos, los encantamientos se aplican pero NO se
> pintan en el lore. `rarity-order` (y, si activas `sort.type`, `type-order`) DEBEN contener los ids
> nuevos (`divino/legendario/epico/raro/comun`). Los cambios de `display` requieren **reiniciar** el
> server (no basta reload).

- **Recargar EcoEnchants** tras editar ymls: su `/ecoenchants reload` **expulsa a los jugadores** y a
  veces hace falta **reiniciar** el server para que ciertos cambios surtan efecto.
- **SuperEnchanter**: tras tocar su `config.yml`/`messages.yml`, `/se reload`. Tras tocar su **código**,
  recompilar (`./gradlew.bat jar`) y **reinicio completo** (NUNCA hot-swap del jar).
- **Auto-merge de config**: al actualizar el jar, las claves nuevas se fusionan sin pisar tus valores,
  pero **NO renombra claves viejas**. Como hemos **renombrado las rarezas**, regenera `config.yml`
  (o borra a mano las entradas con ids viejos `common/rare/...`) para que no queden duplicadas.
- El catálogo canónico se edita en `Ecoenchants_Old` (con backup). Para probar, cópialo al EcoEnchants
  del server de pruebas.

---

## 10. Plantillas copy-paste

### Encantamiento de combate (rareza normal, con gate de EcoSkills)
```yaml
display-name: "Nombre"
description:
  - "Haces un &a%placeholder%%&r más de daño"
placeholder: "10 * %level%"
type: raro
rarity: raro
targets: [sword, axe]
conflicts: []
required: []
max-level: 3
tradeable: false
discoverable: false
enchantable: false
dependencies: [EcoSkills]
effects:
  - id: damage_multiplier
    args: { multiplier: "1 + 0.1 * %level%" }
    triggers: [melee_attack]
conditions:
  - id: has_skill_level
    args:
      skill: combat
      level: 15
      not-met-lines: ["&7Requiere &aCombate &7nivel &eXV"]
```

### Hechizo activo (Habilidad, alt_click + cooldown)
```yaml
display-name: "Nombre"
description: ["Descripción del hechizo"]
type: spell
rarity: legendario
targets: [sword]
conflicts: []
max-level: 1
tradeable: false
discoverable: false
enchantable: false
effects:
  - effects:
      - id: <efecto>
        args: { ... }
      - id: play_sound
        args: { sound: entity_player_levelup, pitch: 1, volume: 1 }
    args: { cooldown: 120, send_cooldown_message: true }
    triggers: [alt_click]
conditions: []
```

### Maldición (curse, el efecto recae en el jugador)
```yaml
display-name: "Maldición de X"
description: ["Tienes un &a%placeholder%%&r de sufrir X al golpear"]
placeholder: "15 * %level%"
type: curse
rarity: epico
targets: [sword, axe]
conflicts: []
max-level: 1
tradeable: false
discoverable: false
enchantable: false
effects:
  - id: <efecto negativo>
    args: { chance: "15 * %level%" }
    triggers: [melee_attack]
    mutators: [ { id: player_as_victim } ]
conditions: []
```

---

## 11. Ejemplo de revisión (antes → después)

**Antes** (`legendario/sangrado.yml` — condición rota, type/rarity viejos):
```yaml
type: legendary
rarity: legendary
effects:
  - id: bleed
    args: { chance: "1.5 * %level%", damage: 1, interval: 15, amount: "2 * %level%" }
    triggers: [melee_attack]
conditions:
    args:                       # ← HUÉRFANO: sin "- id: has_skill_level"; no hace nada
      skill: farming
      level: 20
      not-met-lines: ["&dRequiere tener &aAgricultura &dal nivel &eXX&d."]
```
**Después** (type/rarity migrados, condición reparada y coherente):
```yaml
type: legendario
rarity: legendario
dependencies: [EcoSkills]
effects:
  - id: bleed
    args: { chance: "1.5 * %level%", damage: 1, interval: 15, amount: "2 * %level%" }
    triggers: [melee_attack]
conditions:
  - id: has_skill_level
    args:
      skill: combat            # un enchant de espada gatea por Combate, no Agricultura
      level: 20
      not-met-lines: ["&7Requiere &aCombate &7nivel &eXX"]
```
