# Plan de estudio — Skill de MAGIA y su bucle de profesión

> Documento de diseño (no implementado aún). Objetivo: convertir la mesa de encantamientos
> de un **sumidero** en un **bucle de progresión RPG** mediante una 5ª skill de EcoSkills
> ("Magia") que se sube encantando y, a cambio, mejora el propio encantar y desbloquea el
> uso de hechizos vía **maná**.

---

## 0. TL;DR (la visión en 5 líneas)

1. **Encantas** en la mesa custom → ganas **XP de Magia** (vía la API de EcoSkills).
2. **Magia sube** → te da **mejor probabilidad de éxito**, **coste más barato** y **+maná**.
3. **Más maná** → puedes **lanzar hechizos** (encantamientos `type: spell`, clic derecho, `mana_cost`).
4. Mejor encantar → mejor equipo → más combate/minería → más recursos → más encantar.
5. **El bucle se cierra**: la mesa deja de ser solo "pagar y rezar" y pasa a ser una **profesión**.

```
        ┌─────────────────────────────────────────────────────┐
        │                                                     │
        ▼                                                     │
   ENCANTAS  ──►  +XP Magia  ──►  Magia sube  ──►  +éxito / -coste / +maná
   (mesa)                                              │           │
        ▲                                              │           ▼
        │                                              │      LANZAS HECHIZOS
        └──────────  mejor equipo  ◄────────────────────┘      (spell + maná)
```

---

## 1. Qué nos regala EcoSkills (no hay que inventar casi nada)

EcoSkills ya trae, instalado y configurado en el server, todo el andamiaje:

| Pieza | Estado | Para qué la usamos |
|---|---|---|
| **Skills configurables** (`/skills/*.yml`) | ✅ existe `enchanting.yml` por defecto | Base de la skill "Magia" (renombrar/tematizar) |
| **Sistema de MANÁ** (`/magic_types/mana.yml`) | ✅ configurado (`limit = 100 + wisdom`, regen 2%/s) | Recurso para lanzar hechizos |
| **Stat `wisdom`** | ✅ (`+1% XP skill` y `+1 maná` por punto) | Recompensa por nivel de Magia (sube maná) |
| **Recompensas por nivel** (stats/efectos) | ✅ | wisdom, crit_chance, etc. al subir Magia |
| **`level-up-effects`** (dar dinero/items/comandos al subir) | ✅ | Recompensas tangibles por nivel |
| **API Java** `EcoSkillsAPI.giveSkillXP(player, skill, amount)` | ✅ (compileOnly EcoSkills ya está en `build.gradle`) | Dar XP de Magia desde la mesa custom |
| **Lectura de nivel** | `%ecoskills_magia%` (PAPI) o API | Aplicar beneficios según nivel |
| **Multiplicadores de XP por permiso** (`ecoskills.xpmultiplier.<n>`) | ✅ | XP boost para VIPs sin tocar nada |

> **Gotcha clave:** la skill `enchanting` de EcoSkills gana XP del **encantamiento VANILLA**,
> que tu mesa custom **NO dispara**. Por eso la XP de Magia la **damos nosotros por API** en
> `EnchantingGUI`, y los efectos nativos de esa skill (`second_chance`, `reimbursement`…) **no
> saltan** con la mesa custom → los beneficios "de encantar" los implementa **SuperEnchanter**.

---

## 2. Arquitectura: dos lados

### Lado A — EcoSkills (solo config, cero código)
- Crear `skills/magia.yml` (copiando `enchanting.yml`, tematizado en español).
  - `xp-gain-methods: []` (la XP entra solo por API; sin trigger vanilla).
  - `rewards`: **wisdom** (maná + XP) y opcional crit/otra stat.
  - `level-up-effects`: dinero/items al subir (sink inverso / recompensa).
  - `xp-requirements`: curva propia (ver §4).
- Habilitar `magia` y **deshabilitar** `enchanting` en `EcoSkills/config.yml → skills:`.
- (Maná ya está; los hechizos lo consumirán vía sus ymls.)

### Lado B — SuperEnchanter (código)
1. **Dar XP**: en `EnchantingGUI.handleLevelClick`, por cada **nivel ganado**, llamar
   `EcoSkillsAPI.giveSkillXP(player, magiaSkill, magiaXpFor(rarity))`.
2. **Leer nivel**: `int magia = ecoSkillsHook.getLevel(player, "magia")` (API o PAPI).
3. **Aplicar beneficios** (según `magia`):
   - +éxito en la tirada (`EnchantFormulas.effectiveChance` recibe un bonus extra).
   - −coste (un descuento por nivel en `CostService`/`EnchantingLogic`, junto al de permisos).
   - (opcional) **gating**: bloquear rarezas altas hasta cierto nivel de Magia.
4. **Config nueva** `enchanting.magia` (enabled, skill-id, xp por rareza, curvas de beneficio, gating).
5. **Hook nuevo** `EcoSkillsHook` (softdepend; si EcoSkills falta, Magia se desactiva sola).

---

## 3. Los BENEFICIOS de subir Magia (el corazón)

Cuatro "carriles" de beneficio. Los tres primeros los aplica SuperEnchanter leyendo el nivel; el
cuarto es nativo de EcoSkills.

### Carril 1 — Probabilidad de éxito (reduce la dependencia de sellos)
`bonus = min(cap, magia * paso)`. Propuesta: **+0.5%/nivel, tope +25%** (nivel 50).

```
Éxito de un LEGENDARIO (base 35%) según Magia:
 Magia  0  ██████████ 35%
 Magia 10  ████████████ 40%
 Magia 25  ██████████████ 47%
 Magia 50  ███████████████████ 60%   (tope +25%)
```
→ El novato depende del **Sello de garantía**; el veterano de Magia **ya casi no lo necesita**.
Justo el incentivo del bucle: la skill **sustituye** poco a poco el grindeo de sellos.

### Carril 2 — Descuento de coste
`descuento = min(cap, magia * paso)`. Propuesta: **−0.4%/nivel, tope −20%** (nivel 50).
Se suma al descuento por permiso (gana el mayor o se combinan — decisión de diseño).

```
Coste de un EPICO V (≈ 58 niveles) según Magia:
 Magia  0  ██████████████████████ 58
 Magia 25  ███████████████████ 52  (−10%)
 Magia 50  ██████████████████ 46   (−20%)
```

### Carril 3 — Maná (carril nativo, vía recompensa `wisdom`)
La skill da **wisdom** al subir (p.ej. +1 cada 2 niveles → +25 wisdom a nivel 50).
- `wisdom` → **maná máximo** (`100 + wisdom`) y **+1% XP de skill** por punto.
- Más maná = puedes **lanzar más hechizos** (`type: spell`, alt_click, `mana_cost`).

```
Maná máximo según Magia (con +1 wisdom / 2 niveles):
 Magia  0  ████████████ 100
 Magia 20  ██████████████ 110
 Magia 50  ███████████████ 125
```
→ Esto **resucita la categoría `spell`** (hoy infrautilizada): los hechizos pasan a tener un
recurso real (maná) que **solo creces encantando**. Bucle B.

### Carril 4 — Reembolso Arcano (sub-habilidad, en CÓDIGO) ✅
> Replanteado: las recompensas EcoSkills **nativas** (`second_chance`/`reimbursement`/
> `overcompensation`) se **descartaron** porque solo disparan al encantar **vanilla** (bloqueado en
> este server) → estaban muertas. En su lugar, la 4ª sub-habilidad la implementa SuperEnchanter:
- Al **fallar** un peldaño, prob. `min(max-percent, nivel*per-level)` (default 0.6/nivel, tope 30% a
  nivel 50) de **recuperar el coste** de ese peldaño (`enchanting.magia.refund`,
  `CostService.refund` — XP/Vault/PP). Suaviza el sumidero del `success-chance`.
- La única recompensa nativa que queda en el yml es `wisdom +1/nivel` (= Carril 3, maná).

### (Opcional, decisión tuya) Carril 5 — Gating por nivel
Requerir Magia mínima para encantar rarezas altas:

| Rareza | Magia requerida (propuesta) |
|---|---|
| Común / Raro | 0 |
| Épico | 10 |
| Legendario | 25 |
| Divino | 40 (+ premium) |

→ Convierte Magia en una **puerta de progresión** (el motor de bucle más fuerte), pero es
restrictivo. **Configurable y por defecto OFF** hasta que decidas.

> ❌ **Lo que Magia NO toca: las maldiciones.** La tirada de maldición se queda como gamble
> puro (decisión de diseño previa: prevenir mata la emoción). Magia no inmuniza ni reduce curse.

---

## 4. El bucle de XP (cuánto y cómo)

### XP de Magia por encantar
Por **nivel ganado**, ponderado por rareza (currency-agnóstico; divino paga en PP pero da XP igual):

| Rareza | XP Magia / nivel ganado | XP por subir a V (5 niveles) |
|---|---|---|
| Común | 2 | 10 |
| Raro | 5 | 25 |
| Épico | 12 | 60 |
| Legendario | 25 | 125 |
| Divino | 50 | 250 |

- En **fallo** de un peldaño: **25% del valor** ("aprendes del error") — mantiene el avance aun con mala suerte.
- Multiplicadores de XP por permiso (`ecoskills.xpmultiplier`) aplican gratis (EcoSkills).

### Curva de niveles (reusar la de `enchanting.yml`, que ya está calibrada)
`xp-requirements`: 10, 15, 30, 45, 60, 75, 100, 150, 200, 350, 500, 750, 1000, 1500, 2000, 3000,
5000, 7500, 10000, 20000 … hasta ~44 niveles (260k al final).

**Tiempo estimado (orientativo):**

```
Nivel de Magia   XP acumulada    ≈ ítems llevados a V
   5                 160          ~6 épicos        (tarde-temprano)
  10               ~1.000         ~17 épicos       (mid game)
  20              ~25.000         cientos          (late)
  44 (top)         millones       grind serio      (prestigio)
```
→ Los primeros niveles llegan **rápido** (engancha); los altos son **prestigio** de largo plazo.

---

## 5. Plan de implementación por fases

| Fase | Qué | Esfuerzo | Riesgo |
|---|---|---|---|
| **F1 · Cimientos** ✅ | Puente vía **SuperCore** (no `EcoSkillsHook` propio) + `MagiaService` + dar XP por nivel ganado + leer nivel. La skill SUBE encantando. | Medio | Bajo |
| **F2 · Beneficios mesa** ✅ | Carriles 1 (éxito) y 2 (descuento) leídos del nivel de Magia, + feedback de XP en el action bar. | Bajo-medio | Bajo |
| **F3 · Maná/hechizos** ✅ | Skill rediseñada (`enchanting.yml` = "Magia", solo `wisdom +1/nivel`, `max-level: 50`, `xp-formula`) + `mana_cost` en los 4 hechizos `spell` de `enchants/habilidad/`. **Visibilidad del maná = SOLO al lanzar** (decisión: el action bar persistente de EcoSkills pisaría el feedback de encantar del plugin → sin HUD). | Bajo (casi todo config) | Bajo |
| **F4 · Pulido** ✅ (gating sigue OFF) | 4ª sub-habilidad **Reembolso Arcano** (Carril 4) + visibilidad de las 4 sub-habilidades con valores REALES (icono de nivel `appendMagiaLore`, fix de `appendChanceLore`, expansion PlaceholderAPI `superenchanter` para el lore/`reward-messages` de la skill) + rename display `spell`→**"Hechizos"** + lore de la skill pulido (hex, líneas cortas). Gating (Carril 5) queda OFF a propósito (softgate por curva). | Bajo | Bajo |

**F1+F2+F3+F4 HECHAS (2026-06-08).** F1/F2/F4-código = SuperEnchanter (compilado, desplegado);
F3 + lore de la skill = config de server (EcoSkills `skills/enchanting.yml` + `config.yml` panel maná
+ EcoEnchants `enchants/habilidad/*` con `mana_cost` y `config.yml` grupo "Hechizos").
⚠️ A vigilar en vivo: que el aviso nativo de "sin maná" al lanzar un hechizo se vea (libreforge
price system; si fuese silencioso, añadir mensaje explícito). ⚠️ EcoSkills NO recarga los magic
types (`/ecoskills reload` → WARN "price factory already registered for mana", inofensivo): cambios
de maná/skill exigen **reinicio completo**.

---

## 6. Decisiones que necesito de ti (antes de picar)

1. **Beneficios**: ¿+0.5% éxito y −0.4% coste por nivel (tope +25% / −20% a nivel 50)? ¿Otros números?
2. **Gating** (carril 5): ¿sí o no? Si sí, ¿qué niveles por rareza?
3. **XP por rareza**: ¿la tabla de §4 te cuadra o más/menos generosa?
4. **Descuento de Magia vs permiso**: ¿se suman, o gana el mayor?
5. **Nombre/tema** de la skill: "Magia", icono, color.
6. **Hechizos**: ¿quieres que diseñemos 2-3 hechizos `spell` con maná para estrenar el carril 3?

---

## 7. Comparación / alternativas consideradas

| Enfoque | Pros | Contras | Veredicto |
|---|---|---|---|
| **Magia como skill EcoSkills + XP por API** (este plan) | Reusa todo EcoSkills (maná, stats, GUI, recompensas); bucle completo | Hay que dar XP por API y replicar beneficios en SE | ✅ **Elegido** |
| Skill `enchanting` nativa tal cual | Cero código | Gana XP del encantar VANILLA (bloqueado) → **no sube nunca** | ❌ |
| "Niveles de Magia" propios en SuperEnchanter (sin EcoSkills) | Control total | Reinventar maná/GUI/stats; duplicar EcoSkills | ❌ sobre-ingeniería |
| Magia que reduce maldiciones | — | Rompe la decisión "el curse es gamble puro" | ❌ descartado |

---

## 9. Maná como SISTEMA CORE — análisis profundo

> El usuario no usaba maná, pero quiere que **los hechizos (`spell`) Y los items de MythicMobs
> dependan de él**. Eso no es una feature: es **adoptar un recurso de habilidad a nivel de server**.
> Análisis para decidir con los ojos abiertos.

### 9.1 La decisión de verdad: ¿abrazas el modelo "recurso de habilidad" (RPG class)?
Una vez que las habilidades **dependen** del maná, no hay marcha atrás fácil: pasa a ser una
segunda barra que el jugador gestiona, que tú balanceas para siempre, y que define la identidad
del server hacia lo RPG-clase. **Pros**: profundidad, build de "mago", da sentido a Magia,
rescata `spell`, anti-spam elegante, patrón que el jugador entiende al instante. **Contras**:
sistema nuevo que enseñar y mantener; carga de balance (cada habilidad necesita coste); riesgo de
sentirse "MMO" si no encaja con la vibe survival.

### 9.2 La realidad técnica — DOS familias de plugins (esto es lo importante)
El maná es de la familia **Auxilor/libreforge** (EcoSkills). MythicMobs es **otra familia**.

| Quién consume maná | ¿Nativo? | Cómo |
|---|---|---|
| **Hechizos `spell`** (EcoEnchants) | ✅ **SÍ, nativo** | Arg `mana_cost` + condiciones `has_mana`/`below_magic` en el yml. **CERO código.** |
| **Items de MythicMobs / Crucible** | ❌ **NO nativo** | MM no es libreforge → no conoce `mana_cost`. **Hace falta un puente.** |

**El puente para MM SÍ es posible** (la API expone `getMagic`/`setMagic`/`getMaxMagic`): un MM
skill puede **comprobar** maná con `%ecoskills_mana%` (PAPI) y **descontarlo** llamando a un
comando/hook que SuperEnchanter exponga (p.ej. `/se mana use <jugador> <coste>` → `setMagic`).
Es **custom glue** (no trivial, pero factible). Alternativa: usar **EcoItems** (familia Auxilor,
maná nativo) para items mágicos en vez de MM/Crucible — pero es adoptar otro plugin.

> **Conclusión técnica:** hechizos = gratis e inmediato; items MM = funciona pero requiere un
> pequeño puente que construyes tú. No mezcles las dos cosas en el primer paso.

### 9.3 Principios de diseño (si se hace)
1. **Maná UNIVERSAL** (todos con base ~100). Las habilidades se usan **desde nivel 1**; Magia/
   wisdom **EXPANDE** el pool (progresión, no candado). Evita "el novato no puede lanzar nada".
2. **Roles distintos de maná vs cooldown** (si no, son redundantes):
   - **maná** = *presupuesto de ráfaga* (cuántas habilidades seguidas).
   - **cooldown** = *anti-spam por habilidad concreta*.
   - Habilidades pequeñas/utilidad: **poco maná, poco/ningún cooldown** (spameables dentro del pool).
   - Habilidades gordas: **mucho maná + cooldown** (raras, impactantes).
3. **Visibilidad OBLIGATORIA**: activar la action bar de maná de EcoSkills (vanilla, sin pack).
   Sin verlo, el jugador no entiende por qué su habilidad "no va".
4. **Regen** calibrada para combate (2%/s de 100 = 2/s → un hechizo de 30 tarda 15s en recuperarse).

### 9.4 Camino recomendado (cauto, reversible)
```
Piloto      →  Maná en 2-3 hechizos spell (mana_cost en el yml). CERO código.
(reversible)   Reversible al 100% (quitas mana_cost). Ves si la gente engancha.
   │
   ▼ si funciona
Expandir    →  Más hechizos + action bar de maná + recompensa wisdom de Magia.
   │
   ▼ solo si el maná ya gusta
Bridge MM   →  Comando/hook de descuento de maná para items MythicMobs (custom glue).
```
**No comprometas todo el server al maná antes del piloto.** El piloto de hechizos es casi gratis
y 100% reversible; el bridge de MM es trabajo custom que solo merece la pena si el maná ya prendió.

### 9.5 Veredicto
- **Sí, el maná es buena idea** — es el pago natural de Magia y rescata `spell`. Pero es un
  **compromiso de sistema**, no una feature suelta.
- **Empieza estrecho** (solo hechizos, nativo, reversible). Valida. Luego amplía.
- **Items MM con maná = más tarde**, con un puente (factible vía `setMagic`), no en el día 1.
- Decisión de fondo que solo tú puedes tomar: **¿quieres que DarkMines tenga una barra de maná
  como recurso de habilidad?** Si la respuesta es "sí, me mola el rollo mago", adelante por fases.

---

## 8. Resumen ejecutivo

- **Factible y barato**: EcoSkills aporta el 80% (maná, wisdom, skill, recompensas, API).
- **SuperEnchanter** solo: da XP por API, lee el nivel, aplica éxito/descuento (±gating).
- **Resultado**: la mesa pasa de sumidero a **profesión con bucle** (encantar→Magia→mejor
  encantar) + un **bucle secundario de hechizos** (maná) que rescata la categoría `spell`.
- **Riesgo bajo, alto impacto.** Es el cambio con más recorrido del plugin.
