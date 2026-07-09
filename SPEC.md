# iOS 26 Typography Spec

## Font
- Preference 1: SF Pro Display (licensed)
- Preference 2: Inter Variable
- Preference 3: Geist
- Avoid Roboto as primary

## Hierarchy

| Role | Size | Weight | M3 Slot |
|------|------|--------|---------|
| Título principal | 34sp | SemiBold | headlineLarge |
| Títulos de tarjetas | 24sp | Bold | headlineSmall |
| Nombre del local | 22sp | SemiBold | titleLarge |
| Subtítulos | 17sp | Medium | titleMedium |
| Texto normal | 16sp | Regular | bodyLarge |
| Info secundaria | 14sp | Regular grey | bodyMedium |
| Etiquetas pequeñas | 12sp | Medium | labelMedium |

## Style
- Elegant, light, modern, highly legible
- Excellent spacing, generous line height
- No ALL CAPS text
- Locale names in natural case ("Easy San Bernardo", not "EASY SAN BERNARDO")
- Generous whitespace, strong visual hierarchy
- All buttons/chips/badges/cards use same font family → consistent identity

## Current M3 slots used by component

| Component | Slot | Notes |
|-----------|------|-------|
| HeaderSection title | headlineLarge | → 34sp SemiBold |
| HeaderSection timestamp | bodySmall | → 13sp Regular |
| StatsCards number | headlineMedium | → 28sp SemiBold |
| StatsCards label | labelMedium | → 12sp Medium |
| RouteSearchBar items | bodyLarge | → 16sp Regular |
| DaySelector day name | labelMedium | → 12sp Medium |
| DaySelector day num | labelSmall | → 11sp Medium |
| FilterChips | labelMedium | → 12sp Medium |
| RecentRoutes header | labelMedium | → 12sp Medium |
| RecentRoutes item | labelLarge | → 14sp Medium |
| StoreCard store name | titleLarge | → 22sp SemiBold |
| StoreCard brand name | titleSmall | → 15sp Medium |
| StoreCard brand freq | bodySmall | → 13sp Regular |
| StoreCard address | labelSmall | → 11sp Medium |
| StoreCard empty | bodyMedium | → 14sp Regular |
| DayContent empty | bodyLarge | → 16sp Regular |
| DayContent search hint | bodyMedium | → 14sp Regular |
| MainScreen empty state | bodyLarge | → 16sp Regular |
