# ✦ SuperEnchanter

**Reinventa el encantamiento en Minecraft.** SuperEnchanter sustituye el yunque y la mesa de
encantamientos vanilla por interfaces personalizadas estilo *Hypixel SkyBlock*, con economía,
riesgo, progresión RPG y "librerías encantadas". Es un plugin **privado y exclusivo** del servidor
**DarkMines** — está diseñado para una experiencia de juego concreta, no como producto genérico.

> Hecho sobre **Paper 26.1.2 / Java 25**, integrado con **EcoEnchants**, **MythicMobs**,
> **EcoSkills** (vía SuperCore), **Vault**, **PlayerPoints** y **PlaceholderAPI**.
> **Sin resource packs**: todo se logra exprimiendo Minecraft vanilla al máximo.

---

## 📖 ¿Qué hace, en una frase?

Convierte encantar en una **actividad con decisiones**: eliges qué encantar, asumes un riesgo de
éxito, gastas recursos, puedes mejorar tu suerte con sellos, progresas una profesión ("Magia") que
te hace mejor encantador, construyes una sala de encantamiento real con librerías especiales, y todo
queda registrado para que el staff tenga control total.

---

## 🪄 La Mesa de Encantamientos

Al hacer clic derecho en una mesa de encantamientos se abre una GUI guiada en tres niveles:

1. **Categorías** — los encantamientos agrupados por tipo (Común, Raro, Épico, Legendario, Divino,
   Hechizos…), cada uno con su color.
2. **Encantamientos** de esa categoría.
3. **Niveles** — eliges hasta qué nivel quieres subir.

### Lo que la hace especial

- **Probabilidad de éxito por rareza.** Encantar **no siempre funciona**. Cada rareza tiene su
  probabilidad base (las comunes casi siempre entran; las divinas son una apuesta). **Un fallo
  consume el coste igualmente** — ese es el riesgo y el sumidero de la economía.
- **Subida peldaño a peldaño con checkpoints.** Si pides "Nivel V", el juego sube I→II→III… uno a
  uno. Si un peldaño falla, **conservas todo lo ya conseguido** y solo pierdes ese intento; nunca
  retrocedes. Reintentar solo cuesta lo que falta.
- **Sellos (potenciadores).** Metes un **Sello** de MythicMobs en su ranura para **garantizar** un
  encantamiento de su rareza al **100%**. Un "Sello Raro" asegura los encantamientos raros, etc.
  Hay sellos de cada rareza. Si pones un sello que no toca, el juego te avisa de por qué no aplica.
- **Coste por rareza, acumulativo.** Subir cuesta más cuanto más rara y alta es la mejora. Saltar
  directo a V cuesta lo mismo que subir I→V a mano: la curva es un sumidero real, no un peaje del
  último nivel. **El Divino se paga en una moneda premium** (PlayerPoints), el resto en XP.
- **Reactivos.** Algunos niveles piden un material extra (lapislázuli, amatista…) además del coste.
- **Requisitos visibles ANTES de encantar.** Si un encantamiento exige un nivel de habilidad u otra
  condición, lo ves en el ítem antes de gastar nada.
- **Los bloqueados se muestran con el motivo.** Si un encantamiento no se puede aplicar, aparece en
  gris explicando por qué (incompatible, ya al máximo, te falta otro encantamiento requerido,
  límite de categoría…). Nada de menús que "no hacen nada" sin explicación.
- **Poder de librería.** Las rarezas y niveles altos exigen **poder de estantería** alrededor de la
  mesa (ver abajo).

---

## 📚 Librerías Encantadas — la función estrella

Una **estantería real de Minecraft** (sin texturas custom) que da mucho más poder de encantamiento
y es **detectable e indestructible**.

- Es un ítem de **MythicMobs** (`/se give libreria_encantada`) que, al colocarse, pone una
  *chiseled bookshelf* normal — pero el plugin recuerda que es especial y le da **+10 de poder**
  (frente al poder mínimo de una estantería normal).
- **Indestructible salvo picándola.** No la rompen los pistones, ni el TNT, ni el fuego, ni los
  enderman; solo el jugador picándola, y al romperla **recuperas el ítem** (no se pierde).
- **Se sella sola.** Al colocarla se llena de libros y no se pueden sacar.
- **Requiere línea de visión a la mesa.** Una librería tras una pared no cuenta: la "energía" tiene
  que llegar en línea recta, como en vanilla pero mejor hecho.

El resultado: montar una sala de encantamiento bonita **y funcional** con estanterías reales que de
verdad importan.

---

## ⚒️ El Yunque

Rediseñado como una fusión clara: **objeto + sacrificio → resultado → botón Forjar**.

- **Fusiona** encantamientos de un libro o de otro ítem idéntico sobre tu objeto.
- **Mismas reglas que la mesa.** El yunque respeta exactamente las mismas restricciones
  (conflictos, requisitos, límites, lista negra). Ya no es un "atajo" para colar encantamientos
  prohibidos.
- **No combina ítems que solo comparten material.** No puedes fusionar dos espadas distintas solo
  porque ambas sean "de diamante"; el sacrificio debe ser un libro o un ítem de identidad idéntica.
- **No repara ni renombra** (el servidor desactiva el "mending" a propósito; ver economía).
- **Purifica maldiciones** (ver abajo).

---

## 🔄 Transferir y Extraer encantamientos (la "muela" reconvertida)

Al usar una **muela de afilar (grindstone)** se abre una mesa doble que **mueve** encantamientos
(no los copia, así que el poder total del servidor no se infla). El objeto donante **se consume
entero**.

- **Destino = un objeto** → **transfieres** los encantamientos elegidos a ese objeto.
- **Destino = un libro** → **extraes** los encantamientos a **un libro encantado** (vendible).
- **Multi-selección.** Marcas varios encantamientos y te los llevas **todos en una sola operación**;
  lo que **no** elijas se destruye junto con el donante. Un botón dedicado ejecuta la jugada, así
  que un clic accidental nunca destruye nada.
- **Mismas reglas que la mesa** (compatibilidad, conflictos, etc.).
- Es **más barato que encantar de cero** a propósito: recuperar algo ya creado no debería costar más
  que crearlo.

---

## ☠️ Maldiciones — riesgo y cura

- Al encantar **con éxito** hay una probabilidad **baja** de que el ítem reciba además una
  **maldición** aleatoria. Es un *gamble* inevitable: **no se puede prevenir**.
- **La única salida es el Yunque**, con un **Sello Purificador** (ítem de MythicMobs): pones el
  objeto maldito + el sello y al forjar se eliminan **todas** las maldiciones. Es **gratis** (el
  coste es el propio sello, que se vende caro).
- Las maldiciones vanilla más rotas están vetadas por diseño (p. ej. la de desaparición, porque el
  servidor usa *keepinventory*).

---

## 🔮 La habilidad "Magia" — encantar como profesión

Encantar deja de ser solo un gasto y se convierte en un **bucle RPG**: cada vez que encantas ganas
**XP de Magia**, y subir de nivel de Magia te hace **mejor encantador**.

Al subir de nivel mejoras en varios frentes:

- **➕ Más probabilidad de éxito** al encantar.
- **➖ Menos coste** (descuento que se ve y se cobra de verdad).
- **🔵 Más maná máximo** (el maná lo gastan los Hechizos).
- **♻️ Reembolso Arcano** — al fallar un peldaño, una probabilidad de **recuperar el coste** de ese
  intento, que crece con tu nivel.
- **🔒 Desbloqueo de rarezas altas** — las rarezas Legendario y Divino piden cierto nivel de Magia
  (las bajas no se bloquean; su "puerta" es el % de éxito).

Todo esto se resume en una **cabeza de jugador "Mis estadísticas"** dentro de la mesa, con tu skin,
que muestra tu nivel de Magia y todos tus bonus de un vistazo.

### Equipo de mago

Encantamientos especiales del catálogo que **aceleran tu XP de Magia**:

- **Túnica de Mago** (pechera) → +15% XP de Magia.
- **Sombrero de Mago** (casco) → +25% XP de Magia.
- Puestos a la vez **se acumulan** (~+44%).

Solo aceleran el grindeo de la profesión (no dan poder de combate), así que son una recompensa de
*end-game* para quien se dedica a encantar.

---

## 🪙 Control de la economía

SuperEnchanter cierra todas las vías de conseguir encantamientos "gratis", para que **la única
fuente real sea la mesa custom**:

- **Sin encantamientos gratis en el loot.** Los cofres de estructuras, la pesca y los drops de mobs
  **no dan** libros encantados ni equipo encantado; el equipo aparece limpio.
- **Encantamientos prohibidos a nivel global.** Por defecto el **"mending" (reparación por XP) está
  eliminado** de todo el servidor: rompería la economía de encantar. Se purga de cualquier ítem,
  inventario, pesca o cofre.
- **Aldeanos sin libros.** Los bibliotecarios **no venden** libros encantados, así que no se puede
  saltar la mesa comprando encantamientos baratos.
- **Descuentos por permiso.** Se pueden conceder descuentos (o gratis total) por rango/permiso, que
  se aplican tanto a lo que se muestra como a lo que se cobra.

---

## 🛡️ Anti-duplicación y robustez

- **Modelo "denegar por defecto".** Todas las interacciones peligrosas con las GUIs (shift-clic,
  arrastrar, soltar, teclas numéricas…) están bloqueadas; solo se permite lo seguro. Mover ítems lo
  hace el propio Minecraft, que es la forma correcta.
- **A prueba de crasheos.** Si el servidor se cae con ítems dentro de una GUI, **se te devuelven** al
  reconectar.
- **Registro de auditoría.** Cada operación (forjar, encantar, transferir, extraer, purificar) queda
  guardada con jugador, lugar, ítems, encantamientos y coste.

---

## 🔧 Comandos (`/se` o `/superenchanter`)

| Comando | Qué hace |
|---|---|
| `/se reload` | Recarga configuración y mensajes. |
| `/se give <id> [jugador]` | Da un ítem de MythicMobs del plugin (sellos, librería…). |
| `/se book <encantamiento> <nivel> [jugador]` | Da un libro encantado. |
| `/se bookshelf` | Inspecciona las librerías encantadas del chunk actual. |
| `/se audit [jugador]` | Abre el **visor de auditoría**: una interfaz paginada donde, al pasar el ratón por cada operación, ves los ítems, encantamientos, fecha, lugar y coste. Para el staff. |

---

## 🎨 Filosofía de diseño

- **Sin resource packs.** Todo se consigue con Minecraft vanilla bien explotado: estanterías reales,
  brillos, gradientes de color, interfaces con cabezas de jugador, etc.
- **Diseñado para jugarse, no para configurarse.** Al ser un plugin de un único servidor, los
  valores por defecto **son** la experiencia que se quiere ofrecer, no opciones "apagadas por si
  acaso".
- **Coherencia visual.** Yunque, mesa y transferencia comparten el mismo lenguaje visual para que se
  lean igual de claras.

---

## 🧩 Dependencias

- **EcoEnchants** — obligatorio (catálogo de encantamientos, rarezas y tipos).
- **MythicMobs** — sellos, librerías y el equipo arcano.
- **EcoSkills** (vía **SuperCore**) — la habilidad Magia y el maná. Si falta, Magia se desactiva sola
  sin romper nada.
- **Vault** / **PlayerPoints** — monedas alternativas (dinero / tokens).
- **PlaceholderAPI** — expone los bonus de Magia para scoreboards, descripciones, etc.

---

*SuperEnchanter — plugin privado del servidor DarkMines. Autor: Scrulius.*
