---
description: Revisa los cambios recientes del plugin y actualiza CLAUDE.md
---

Actualiza el resumen del proyecto en `CLAUDE.md` (raíz del proyecto) para que refleje el estado
actual del código.

Pasos:
1. Mira qué ha cambiado desde la última actualización del resumen: `git diff`, `git log --oneline -20`,
   y el estado del árbol (`git status`). Si no hay git, inspecciona los archivos fuente clave.
2. Compara con el contenido actual de `CLAUDE.md` y detecta lo que esté desactualizado o falte:
   nuevas features, cambios de arquitectura, nuevas clases/paquetes, cambios de config,
   decisiones de diseño, gotchas, o ideas pendientes ya resueltas.
3. Edita `CLAUDE.md` ajustando solo las secciones afectadas. Mantén el MISMO formato y estructura.
4. Reglas:
   - Conciso: es un resumen para arrancar en frío, no documentación exhaustiva.
   - Mueve a "Ideas pendientes" lo que se haya implementado / quítalo si ya está en otra sección.
   - No inventes; si dudas de un detalle, verifícalo en el código antes de escribirlo.
   - No toques la nota "🔄 MANTÉN ESTE ARCHIVO ACTUALIZADO" del principio.
5. Al terminar, resume en 2-3 líneas qué secciones actualizaste.

Argumentos opcionales del usuario: $ARGUMENTS (p.ej. "solo la sección de librerías encantadas").
