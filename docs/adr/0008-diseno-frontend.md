# 8. Diseño del frontend: tokens CSS propios, sin librería de UI

Estado: Aceptado

## Contexto
La primera versión del frontend era HTML sin estilos. Había que darle una UI
coherente, accesible y responsive sin que el diseño se convirtiera en el
mayor coste de mantenimiento de un proyecto pequeño.

## Decisión
Un sistema de diseño propio, en CSS plano, sin librería de componentes:

- **Tokens como variables CSS** en `frontend/src/styles.css` (`--primary`,
  `--surface`, `--text`, `--space-*`, `--radius`, `--shadow`...). Los
  componentes consumen tokens, nunca colores literales.
- **Modo claro y oscuro** con `prefers-color-scheme`, redefiniendo solo los
  tokens. Los pares texto/fondo se eligieron para contraste AA (4.5:1 en
  texto, 3:1 en bordes de controles).
- **Clases utilitarias reutilizables** globales (`.btn`, `.card`, `.badge`,
  `.field`, `.alert`, `.skeleton`, `.table`) y CSS específico dentro de cada
  componente Angular (el presupuesto de `anyComponentStyle` es 4 kB).
- **Componentes compartidos con lógica** en `app/shared`: `Pager` y
  `ToastContainer`; el estado de notificaciones vive en `ToastService`
  (signals).
- **Fuente del sistema** (`system-ui`). No se añadió `@fontsource`: una
  fuente propia sumaría una dependencia y ~20-40 kB sin aportar nada que el
  proyecto necesite.
- **Accesibilidad como requisito, no como extra**: foco visible, etiquetas
  asociadas, `role="alert"` en errores, `aria-live` en toasts, enlace de
  salto al contenido y `prefers-reduced-motion`.
- **Sin `innerHTML`**: todo el contenido dinámico pasa por el binding de
  Angular, que escapa por defecto (mitiga XSS; ver ADR 0006).
- **Guard por rol solo como UX**: `roleGuard('ADMIN')` oculta la pantalla de
  administración leyendo el claim `role` del JWT sin verificar la firma. La
  autorización real es del backend (`/api/products/**` exige `ROLE_ADMIN`).

## Alternativas consideradas
- **Angular Material / PrimeNG / Bootstrap**: más rápido al inicio, pero
  añaden decenas de kB, otra API que aprender, actualizaciones mayores
  acopladas a las de Angular y un aspecto genérico. Para ~8 pantallas el
  coste no compensa.
- **Tailwind**: añade toolchain y un vocabulario de clases en cada plantilla;
  innecesario con tokens y unas pocas clases.
- **CSS por componente sin tokens**: duplica colores y rompe la coherencia
  entre modo claro y oscuro.

## Consecuencias
+ Bundle pequeño (~100 kB transferidos) y sin dependencias de UI.
+ Cambiar la marca o el tema es editar un bloque de variables.
- Hay que mantener a mano los componentes (modales, menús, foco). Se evitaron
  los modales: las confirmaciones son en línea.
- No hay selector manual de tema; sigue la preferencia del sistema.
- El contraste AA se verificó calculando la razón de contraste WCAG de los
  pares de tokens usados (claro y oscuro: 0 fallos) con un script puntual; no
  hay una comprobación automatizada en CI, así que un cambio de color debe
  revisarse a mano.
