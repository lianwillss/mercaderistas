# Mercaderistas — Design System

## Typography (iOS 26 Style)

See `SPEC.md` for full mapping.

### Quick reference

| Role | M3 Slot | Size | Weight |
|------|---------|------|--------|
| Título principal | headlineLarge | 34sp | SemiBold |
| Número stats | headlineMedium | 28sp | SemiBold |
| Título tarjeta | headlineSmall | 24sp | Bold |
| Nombre local | titleLarge | 22sp | SemiBold |
| Subtítulo | titleMedium | 17sp | Medium |
| Texto normal | bodyLarge | 16sp | Regular |
| Info secundaria | bodyMedium | 14sp | Regular |
| Etiqueta pequeña | labelMedium | 12sp | Medium |

No ALL CAPS. Natural case for store names.

## Font
Prefer: SF Pro > Inter > Geist. Avoid Roboto.
Currently using system sans-serif (Google Sans on Pixel, modern sans on others).
To truly avoid Roboto, bundle Inter or Geist .ttf in `res/font/` and set `FontFamily` on each `TextStyle` in `Type.kt`.

## Project Files
- `SPEC.md` — full typography spec
- `ui/theme/Type.kt` — M3 Typography definition
- `ui/theme/Color.kt` — M3 color scheme
- `ui/theme/Theme.kt` — MercaderistasTheme composable
