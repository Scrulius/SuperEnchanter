# Plan de diseño — SuperCore (plugin núcleo compartido)

> Documento de diseño (no implementado). SuperCore es un **plugin runtime** que aloja la
> infraestructura **compartida y cross-cutting** de la familia Super* (SuperEnchanter, SuperMines,
> futuros). NO contiene lógica de feature. Su pieza estrella inicial es el **puente MM↔EcoSkills**.

---

## 0. Por qué ahora (y no antes)
La regla que nos pusimos: *"extraer a un Core solo cuando haya tipos/servicios runtime compartidos
de verdad, no por utils duplicados"*. **Esa condición ya se cumple**: el puente MM↔EcoSkills es un
servicio runtime que (a) no cabe en SuperEnchanter por scope, (b) lo necesitan varios plugins, y (c)
comparte tipos que cruzan fronteras entre plugins → exige **un único artefacto runtime** (no shaded).

---

## 1. Carta de constitución (qué ENTRA y qué NO)

> **Regla de oro:** entra lo que **2+ plugins Super\*** usarían, o lo que **puentea 2 plugins
> externos**. La **lógica de feature** nunca entra.

| ✅ ENTRA en SuperCore | ❌ NO entra (vive en su plugin) |
|---|---|
| Hooks de integración (EcoSkills, EcoEnchants, MythicMobs, Vault, PlayerPoints) | Lógica de la mesa/yunque/transfer (SuperEnchanter) |
| **Puente MM↔EcoSkills** (stats, maná, skill XP) | Lógica de minas (SuperMines) |
| Economía compartida (`Cost`, `CostType`, `CostService`) | Encantamientos, fórmulas de coste de una feature |
| Framework de GUI anti-dupe (`AbstractCustomGUI`) + `ItemBuilder` | GUIs concretas (EnchantingGUI, etc.) |
| Utils (`MiniMessageUtil`, `ConfigUpdater`, `CooldownManager`, `PendingItemStore`, `AuditLog`) | — |
| **API/eventos** para comunicación entre plugins (p.ej. `MineService`) | — |

---

## 2. Arquitectura

```
                 ┌──────────────────────────────┐
                 │          SuperCore           │  (plugin runtime, 1 jar)
                 │  hooks · bridge · economy ·  │
                 │  gui · util · api/eventos    │
                 └──────────────────────────────┘
                    ▲            ▲            ▲
        depend ─────┘     depend │      depend└───── depend
                              │            │
       ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
       │ SuperEnchanter│  │  SuperMines  │  │  (futuros)   │
       └──────────────┘  └──────────────┘  └──────────────┘
```
- SuperCore = **plugin runtime** (no shaded). Los Super* lo declaran `depend: [SuperCore]`.
- **Un solo artefacto** → los tipos compartidos (eventos, servicios) existen una vez (sin choques de
  classloader que tendría una librería shaded duplicada).

### Módulos internos (un jar, paquetes separados)
| Paquete | Contenido |
|---|---|
| `core.hooks` | Wrappers softdepend: EcoSkills, EcoEnchants, MythicMobs, Vault, PlayerPoints (con checks de presencia). |
| `core.bridge` | **Puente MM↔EcoSkills** (mecánicas MM custom + sistema de stats por item + servicio de maná). |
| `core.economy` | `Cost`, `CostType`, `CostService`. |
| `core.gui` | `AbstractCustomGUI` (anti-dupe), `ItemBuilder`. |
| `core.util` | `MiniMessageUtil`, `ConfigUpdater`, `CooldownManager`, `PendingItemStore`, `AuditLog`. |
| `core.api` | Servicios públicos + eventos que otros plugins consumen (`SuperCore.get…()`, `MineService`, eventos). |

---

## 3. Pieza estrella — Puente MM↔EcoSkills (spec completa)

EcoSkills (familia Auxilor/libreforge) y MythicMobs (otra familia) no se hablan. El puente expone
EcoSkills a MM **por su API real** (no PAPI frágil ni parsing de lore).

### 3.1 API de EcoSkills disponible (confirmada en el jar)
- Stats: `addStatModifier(player, StatModifier)`, `removeStatModifier(player, uuid)`, `getStatLevel`,
  `giveBaseStatLevel`, `setBaseStatLevel`.
- Maná: `getMagic`, `setMagic`, `getMaxMagic` (+ eventos `MagicEvent`, `PlayerRegenMagicEvent`).
- Skills: `giveSkillXP`, `getSkillLevel`, `setSkillLevel`, `getSkillProgress`.

### 3.2 Mecánicas custom de MythicMobs que registra el puente
Se usan en los YML de MM como cualquier otra mecánica/condición:

| Mecánica / Condición | Qué hace | Implementación |
|---|---|---|
| `ecoStat{stat=strength;amount=10}` | Da un stat de EcoSkills (en skills: buff temporal) | `addStatModifier` con UUID |
| `manaCost{amount=30}` (condición+coste) | Comprueba maná y lo **descuenta** antes de disparar | `getMagic`/`setMagic` |
| `giveMana{amount=20}` / `setMana{amount}` | Da/fija maná | `setMagic` |
| `giveSkillXp{skill=magia;amount=50}` | Da XP de skill | `giveSkillXP` |
| `giveStatLevel{stat=strength;amount=1}` | Sube nivel base de un stat | `giveBaseStatLevel` |
| `aboveMana{amount=50}` / `belowMana{amount}` | Condiciones de umbral de maná | `getMagic` |

### 3.3 Stats en ITEMS de MythicMobs (lo que tu puente actual hace, pero bien)
Los items MM no son holders libreforge → no dan stats nativos. El puente:
1. Marca el item con un **tag/PDC** de stats (p.ej. `supercore:stats = strength:10,crit_chance:5`),
   puesto vía MM (`Options`/NBT) o por el propio puente.
2. **Listener de equipamiento** (PlayerArmorChange, held-item change, inventory click, join, world
   change): escanea las piezas equipadas, aplica `addStatModifier` con un **UUID estable** por
   slot+stat, y lo **retira** al desequipar. Cero drift, cero fugas.
3. Robusto ante swaps, cambio de mundo, offline y muerte con keepinventory.

### 3.4 Por qué esto es "mil veces mejor" que un puente casero
- Stats por **modificador UUID** (no recalcular ni parsear lore) → sin acumulación ni leaks.
- **Mecánicas MM nativas** → se leen en el YML como cualquier mecánica, no comandos.
- **API real** de EcoSkills → robusto a cambios de formato.
- Un solo sitio bien testeado, reutilizable por todos los Super*.

---

## 4. Cómo lo consumen los plugins

### SuperEnchanter (para Magia/maná)
`depend: [SuperCore]`. Usa `SuperCore.hooks().ecoSkills()` para **dar XP de Magia** y **leer el
nivel**; el coste de maná de los hechizos sigue siendo nativo de EcoEnchants. El puente le da, además,
el `manaCost` para items MM relacionados.

### SuperMines (recode, desde día 1)
Se construye sobre SuperCore: economía, GUI anti-dupe, utils y la **API cruzada** (`MineService`,
eventos) para que los encantamientos interactúen con minas (vía libreforge + el bridge).

---

## 5. Fases (sin desestabilizar lo que funciona)

| Fase | Qué | Nota |
|---|---|---|
| **F1 · Esqueleto + Puente** ✅ HECHO | SuperCore con `hooks` + `bridge` (MM↔EcoSkills completo: stats, maná, skill XP + item-stats por id MM). | Proyecto en `C:\Users\Knopp\plugins\SuperCore`. Mecánicas: `ecoStat`/`manaCost`/`giveMana`/`setMana`/`giveSkillXp`/`giveStatLevel`; condiciones `aboveMana`/`belowMana`. Compila contra los jars reales del server (libreforge relocado dentro de EcoSkills). Ver `SuperCore/CLAUDE.md`. |
| **F2 · Consumo SuperEnchanter** | SuperEnchanter `depend` SuperCore para la integración de **Magia** (dar XP, leer nivel). NO se migran sus internals. | El plugin sigue igual; solo lo nuevo pasa por Core. |
| **F3 · SuperMines sobre Core** | El recode nace usando economía/GUI/API de Core. | El momento perfecto para la API cruzada. |
| **F4 · Dedupe (opcional, tarde)** | Migrar utils/economía/GUI de SuperEnchanter a Core cuando convenga. | Solo si la duplicación molesta de verdad. |

> **No migrar SuperEnchanter a la fuerza.** Core nace por el puente; SuperEnchanter lo usa para lo
> NUEVO (Magia). Lo viejo se migra cuando duela, no antes.

---

## 6. Build & versionado
- SuperCore = proyecto/jar propio. Los Super* hacen `compileOnly(SuperCore-api)` y `depend` en runtime.
- Versionar la API de Core (los Super* dependen de una versión mínima). Cambios de API = bump.
- Multi-módulo Gradle opcional (un `:core-api` ligero + `:core-plugin`), o un único jar al principio.

---

## 7. Riesgos y disciplina
| Riesgo | Mitigación |
|---|---|
| **God-plugin / cajón desastre** | La carta (§1): solo cross-cutting; feature fuera. Revisar cada cosa que entra. |
| Orden de carga | `depend` lo garantiza; Core arranca antes. |
| Acoplar todo a Core | Hooks softdepend dentro de Core; si falta un externo, ese sub-servicio se desactiva, no tumba Core. |
| Sobre-diseñar | Construir módulos **cuando un 2º consumidor los pida**, no "por si acaso". El puente es la única pieza con consumidor inmediato. |

---

## 8. Veredicto
- **Sí, SuperCore ahora tiene sentido** — la condición (servicio runtime compartido de verdad) se
  cumple con el puente MM↔EcoSkills.
- **Arranca por F1 (esqueleto + puente completo)**: resuelve tu puente casero, queda reutilizable, y
  es la base que Magia y SuperMines van a necesitar igualmente.
- **Con carta de constitución estricta** para que no degenere en monolito.
- El resto de módulos (economía, GUI, utils) se llenan **cuando un segundo plugin los reclame**, no antes.
