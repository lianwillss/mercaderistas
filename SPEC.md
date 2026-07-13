# iOS 26 Typography Spec

## Font
- Inter (bundled) — closest open alternative to SF Pro
- Single font family for all roles → consistent identity

## Hierarchy

| Role | Size | Weight | M3 Slot |
|------|------|--------|---------|
| Título principal | 34sp | Bold | headlineLarge |
| Números (stats) | 28sp | Bold | headlineMedium |
| Nombre de tarjetas | 20sp | Bold | headlineSmall |
| Nombre del local / Subtítulos | 22sp | SemiBold | titleLarge |
| Texto importante | 17sp | SemiBold | titleMedium |
| Encabezados menores | 15sp | SemiBold | titleSmall |
| Texto normal | 15sp | Regular | bodyLarge |
| Info secundaria | 14sp | Regular | bodyMedium |
| Texto secundario | 13sp | Medium | bodySmall |
| Etiquetas grandes | 14sp | Medium | labelLarge |
| Etiquetas / chips | 12sp | Medium | labelMedium |
| Texto auxiliar | 11sp | Regular | labelSmall |

## Style
- No hardcoded `fontSize`, `lineHeight`, or `fontWeight` on individual Composables — all inherit from `MaterialTheme.typography`
- iOS 26 inspired: clean, premium, highly legible
- Generous white space, strong visual hierarchy
- Locale names in natural case ("Easy San Bernardo", not "EASY SAN BERNARDO")
- All buttons, chips, badges, cards use same font family

## M3 slots used by component

| Component | Slot | Notes |
|-----------|------|-------|
| HeaderSection title | headlineLarge | 34sp Bold |
| HeaderSection timestamp | bodyMedium | 14sp Regular |
| HeaderSection urgency badge | labelLarge | 14sp Medium |
| StatsCards number | headlineSmall | 20sp Bold |
| StatsCards "N con promo" badge | bodySmall | 13sp Medium |
| RouteSearchBar items | bodyLarge | 15sp Regular |
| RouteSearchBar suggestions | bodyLarge | 15sp Regular |
| DaySelector selected name | titleSmall | 15sp SemiBold |
| DaySelector unselected name | labelLarge | 14sp Medium |
| FilterChips all | labelLarge | 14sp Medium |
| FilterChips brand selected | titleSmall | 15sp SemiBold |
| FilterChips brand unselected | labelLarge | 14sp Medium |
| RecentRoutes header | labelLarge | 14sp Medium |
| RecentRoutes item | bodyLarge | 15sp Regular |
| StoreCard store name | titleLarge | 22sp SemiBold |
| StoreCard brand name | titleSmall | 15sp SemiBold |
| StoreCard brand freq | labelLarge | 14sp Medium |
| StoreCard address | bodySmall | 13sp Medium |
| StoreCard empty | bodyMedium | 14sp Regular |
| PromoDaySection title | titleSmall | 15sp SemiBold |
| PromoDaySection urgency | labelLarge | 14sp Medium |
| PromoDaySection chain | labelMedium | 12sp Medium |
| PromotionsScreen title | headlineLarge | 34sp Bold |
| PromotionsScreen brand card | headlineSmall | 20sp Bold |
| PromotionsScreen chain name | titleSmall | 15sp SemiBold |
| PromotionsScreen product name | titleSmall | 15sp SemiBold |
| PromotionsScreen price | headlineSmall | 20sp Bold |
| PromotionsScreen dates | bodySmall | 13sp Medium italic |
| AlertDialog title | headlineSmall | 20sp Bold |
| AlertDialog body | bodyLarge | 15sp Regular |
| ManualScreen section title | titleSmall | 15sp SemiBold |
