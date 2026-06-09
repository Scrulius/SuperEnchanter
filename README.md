# SuperEnchanter

SuperEnchanter sustituye el yunque y la mesa de encantamientos de Minecraft por interfaces propias
que reorganizan por completo cómo se obtienen y gestionan los encantamientos. No es un añadido de
encantamientos sueltos: es un rediseño del sistema de encantamiento como mecánica de juego, con
coste, riesgo, progresión y control económico.

Es un plugin privado, desarrollado en exclusiva para el servidor **DarkMines**. Por eso sus valores
por defecto no buscan ser "neutros" ni compatibles con instalaciones de terceros, sino reflejar
directamente la experiencia que el servidor quiere ofrecer.

Está construido sobre Paper 26.1.2 (Java 25) e integra EcoEnchants, MythicMobs, EcoSkills (a través
de SuperCore), Vault, PlayerPoints y PlaceholderAPI. Una decisión de diseño transversal es la
ausencia total de *resource packs*: todo el resultado visual y funcional se consigue con mecánicas de
Minecraft vanilla llevadas al límite.

---

## Visión general

El objetivo del plugin es que encantar deje de ser una acción trivial y gratuita y pase a ser una
actividad con decisiones y consecuencias. El jugador elige qué encantar y hasta qué nivel, asume una
probabilidad de éxito, gasta recursos que se pierden incluso al fallar, puede invertir en mejorar su
suerte, y progresa una profesión que lo convierte en mejor encantador con el tiempo. En paralelo, el
plugin cierra todas las vías alternativas de conseguir encantamientos (loot, comercio, etc.) para que
la mesa custom sea la única fuente real, y registra cada operación para dar control al staff.

---

## La mesa de encantamientos

Al interactuar con una mesa de encantamientos se abre una interfaz organizada en tres niveles de
navegación: primero las categorías (los tipos de encantamiento, agrupados y diferenciados por color),
después los encantamientos disponibles dentro de la categoría elegida, y por último los niveles a los
que se puede subir cada encantamiento.

### Probabilidad de éxito

Encantar no garantiza el resultado. Cada rareza tiene una probabilidad de éxito base distinta: los
encantamientos comunes entran casi siempre, mientras que los de rareza alta son una apuesta real
(aproximadamente 80% para común, 65% raro, 35% épico, 20% legendario y 10% divino, ajustables por
configuración). Un intento fallido consume igualmente su coste y sus reactivos. Esa pérdida es el
riesgo central del sistema y el principal sumidero de la economía.

### Subida por peldaños con puntos de control

Cuando el jugador pide subir un encantamiento a un nivel concreto, el proceso avanza nivel a nivel en
lugar de resolverse de una vez. Cada peldaño cobra su coste y tira su probabilidad por separado. Si
un peldaño falla, el ascenso se detiene pero se conservan todos los niveles ya conseguidos: el
jugador nunca retrocede ni pierde más que el intento actual. Reintentar más tarde solo cuesta lo que
falta, porque el coste acumulado descuenta lo ya alcanzado.

### Sellos

Los sellos son objetos (definidos en MythicMobs) que se colocan en una ranura propia de la mesa y
garantizan al cien por cien un encantamiento de una rareza concreta. Existe un sello por rareza; un
sello solo afecta a su rareza y solo se consume cuando se aplica a ella. Si el jugador coloca un
sello que no corresponde a la rareza que está encantando, la interfaz se lo indica para que entienda
por qué no surte efecto. Son la forma de eliminar el riesgo en las jugadas importantes, a cambio de
un objeto valioso.

### Coste y monedas

El coste depende de la rareza y del nivel, y es acumulativo: subir directamente a un nivel alto
cuesta lo mismo que haberlo subido peldaño a peldaño, de modo que la curva funciona como un sumidero
real y no como un simple peaje del último nivel. Por defecto se cobra en experiencia, pero la rareza
Divina se cobra en una moneda premium (PlayerPoints), reforzando su carácter de objetivo de fin de
juego. Algunos niveles exigen además un reactivo material (por ejemplo lapislázuli o amatista).

### Información antes de actuar

La interfaz prioriza que el jugador sepa lo que va a pasar antes de gastar nada. Los encantamientos
muestran sus requisitos de uso (por ejemplo, exigir cierto nivel de una habilidad) directamente en su
descripción. Los encantamientos que no se pueden aplicar aparecen en gris con el motivo concreto:
incompatibilidad con otro encantamiento, nivel ya máximo, falta de un encantamiento requerido o
límite de su categoría alcanzado. Una cabeza de jugador con la apariencia del propio jugador resume,
en un solo sitio, sus estadísticas relevantes de encantamiento.

---

## Librerías encantadas

Las librerías encantadas son la pieza más distintiva del plugin. Se trata de estanterías reales de
Minecraft (estanterías cinceladas vanilla, sin texturas personalizadas) que aportan mucho más poder
de encantamiento que una estantería normal y que el plugin trata como objetos especiales.

Se obtienen como un ítem de MythicMobs y, al colocarse, dejan una estantería normal a la vista pero
quedan marcadas internamente para otorgar un bloque de poder muy superior al de una estantería
corriente. Esa marca persiste con el mundo.

Una librería colocada es prácticamente indestructible salvo que el jugador la pique directamente: no
la afectan los pistones, las explosiones de TNT, el fuego ni mobs como los enderman. Al picarla, el
jugador recupera el ítem original en lugar de perderlo. Además, al colocarse se sella sola: se llena
de libros que no se pueden extraer.

El poder de las librerías se calcula exigiendo línea de visión real con la mesa. Una librería situada
detrás de una pared no aporta nada; la conexión debe ser directa, igual que en el comportamiento
vanilla pero aplicado con más rigor. El efecto práctico es que construir una sala de encantamiento
deja de ser decorativo y pasa a ser una inversión funcional con estanterías que importan de verdad.

---

## El yunque

El yunque se ha rediseñado como una operación de fusión clara, con dos entradas (el objeto y el
sacrificio), una previsualización del resultado y un botón explícito para forjar.

Su característica principal es que aplica exactamente las mismas reglas que la mesa de
encantamientos. Históricamente el yunque vanilla era una vía para saltarse restricciones (por
ejemplo, colar mediante fusión un encantamiento que la mesa prohíbe); aquí el yunque respeta los
mismos objetivos válidos, conflictos, requisitos, límites por categoría y lista negra que la mesa.

El yunque tampoco permite combinar dos objetos que solo comparten material base. No es posible
fusionar dos espadas distintas por el simple hecho de que ambas sean de diamante: el sacrificio debe
ser un libro encantado o un objeto de identidad idéntica a la del objeto principal. Por decisión de
diseño, el yunque no repara durabilidad ni renombra objetos. Su otra función relevante es la
purificación de maldiciones, descrita más abajo.

---

## Transferencia y extracción de encantamientos

La muela de afilar (grindstone) se reutiliza como una mesa de transferencia y extracción. En lugar de
copiar encantamientos, los mueve, de modo que el poder total presente en el servidor no se infla. El
objeto donante se consume por completo en la operación.

El comportamiento depende de qué se coloque como destino. Si el destino es un objeto, los
encantamientos seleccionados se transfieren a ese objeto. Si el destino es un libro normal, los
encantamientos se extraen a un libro encantado, que es un objeto vendible. La selección es múltiple:
el jugador marca los encantamientos que quiere llevarse y un botón dedicado ejecuta todos a la vez;
lo que no se selecciona se destruye junto con el donante. El uso de un botón dedicado evita que un
clic accidental destruya el donante.

La validación es idéntica a la de la mesa: se respetan compatibilidades, conflictos, requisitos y
límites del objeto de destino. El coste está deliberadamente ajustado para ser más barato que
encantar desde cero, ya que recuperar o reubicar algo ya creado no debería salir más caro que
crearlo.

---

## Maldiciones

El sistema de maldiciones introduce un riesgo añadido al encantar. Cuando un encantamiento se aplica
con éxito, existe una probabilidad baja de que el objeto reciba además una maldición aleatoria. Esa
probabilidad no se puede prevenir de ninguna forma: es un riesgo inherente al acto de encantar y los
sellos no protegen contra ella.

La única manera de retirar maldiciones es el yunque, mediante un Sello Purificador (un objeto de
MythicMobs). Colocando el objeto maldito junto al sello y forjando, se eliminan todas las maldiciones
del objeto y el sello se consume. La operación es gratuita en términos de coste: el "precio" es el
propio sello, que es un objeto valioso. Las maldiciones vanilla que romperían la experiencia del
servidor (por ejemplo la de desaparición, dado que el servidor conserva el inventario al morir) están
excluidas por configuración.

---

## La habilidad Magia

Magia convierte el encantamiento en una profesión con progresión. Cada vez que el jugador encanta
gana experiencia de Magia, y al subir de nivel mejora su propia capacidad de encantar. El sistema
requiere EcoSkills a través de SuperCore; si esa infraestructura no está presente, Magia se desactiva
por sí sola sin afectar al resto del plugin.

Subir de nivel de Magia mejora varios aspectos de forma simultánea:

- Aumenta la probabilidad de éxito al encantar (hasta un tope).
- Reduce el coste de encantar, tanto en lo que se muestra como en lo que se cobra.
- Incrementa el maná máximo del jugador, que es el recurso que consumen los hechizos.
- Activa el Reembolso Arcano: al fallar un peldaño, una probabilidad creciente de recuperar el coste
  de ese intento.
- Desbloquea las rarezas altas: Legendario y Divino exigen cierto nivel de Magia para poder
  encantarse. Las rarezas bajas no se bloquean, porque su barrera natural es la probabilidad de
  éxito, no un candado.

Todos estos beneficios se concentran en una cabeza de jugador dentro de la mesa que muestra el nivel
de Magia y los bonus actuales del jugador.

Como contenido de fin de juego para esta profesión existe un equipo de mago: la Túnica de Mago y el
Sombrero de Mago son encantamientos que aceleran la ganancia de experiencia de Magia (un +15% y un
+25% respectivamente, acumulables hasta cerca de un +44% con ambas piezas equipadas). No otorgan
poder de combate ni de economía; su único efecto es acelerar el progreso de quien se dedica a
encantar.

---

## Control de la economía

Para que la mesa custom sea la única fuente real de encantamientos, el plugin cierra el resto de
vías por las que normalmente se obtienen de forma gratuita:

- El loot natural no entrega encantamientos. Los cofres de estructuras, la pesca y los objetos que
  sueltan los mobs aparecen sin encantamientos: los libros encantados se eliminan y el equipo se
  entrega limpio.
- Existe una lista de encantamientos prohibidos a nivel global. Por defecto incluye la reparación por
  experiencia ("mending"), que se elimina de cualquier objeto, inventario, captura de pesca o cofre,
  porque su presencia rompería la economía basada en gastar experiencia para encantar.
- Los aldeanos no comercian con libros encantados, de modo que no es posible saltarse la mesa
  comprando encantamientos baratos a un bibliotecario.
- Se pueden conceder descuentos por permiso o rango, incluido el coste gratuito total, que se aplican
  de forma coherente tanto al precio mostrado como al cobrado.

---

## Anti-duplicación y robustez

Las interfaces del plugin parten de un modelo de "denegar por defecto": toda interacción potencialmente
peligrosa (clic con mayúsculas, arrastrar, soltar, teclas numéricas, etc.) está bloqueada, y solo se
habilitan las operaciones seguras, dejando que sea el propio Minecraft quien mueva los objetos. Si el
servidor se cae con objetos dentro de una interfaz, estos se devuelven al jugador al reconectar.

Cada operación relevante (forjar, encantar, transferir, extraer, purificar) se registra en un sistema
de auditoría con el jugador, la ubicación, los objetos implicados, sus encantamientos y el coste, lo
que da al staff un rastro completo para investigar anomalías o abusos.

---

## Comandos

El comando principal es `/superenchanter`, con el alias `/se`.

| Comando | Función |
|---|---|
| `/se reload` | Recarga la configuración y los mensajes. |
| `/se give <id> [jugador]` | Entrega un objeto de MythicMobs del plugin (sellos, librería, etc.). |
| `/se book <encantamiento> <nivel> [jugador]` | Entrega un libro encantado. |
| `/se bookshelf` | Inspecciona las librerías encantadas marcadas en el chunk actual. |
| `/se audit [jugador]` | Abre el visor de auditoría: una interfaz paginada en la que, al situar el cursor sobre cada operación, se muestran los objetos, encantamientos, fecha, lugar y coste. Pensado para el staff. |

---

## Dependencias

- **EcoEnchants** (obligatorio): proporciona el catálogo de encantamientos, las rarezas y los tipos.
- **MythicMobs**: define los sellos, las librerías encantadas y el equipo arcano.
- **EcoSkills** mediante **SuperCore**: sostiene la habilidad Magia y el maná. Su ausencia desactiva
  esa parte sin afectar al resto.
- **Vault** y **PlayerPoints**: monedas alternativas (dinero y tokens).
- **PlaceholderAPI**: expone los valores de Magia para scoreboards, descripciones y otros usos.

---

Plugin privado del servidor DarkMines. Autor: Scrulius.
