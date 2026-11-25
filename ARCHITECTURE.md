# Arquitetura da AlphaStore

Porta nativa em Jetpack Compose da antiga AlphaStore Legacy (Capacitor). Activity única, MVVM, Material 3 Expressive.

## Stack

| Camada | Escolha |
|---|---|
| UI | Compose BOM 2026.09.00, material3 1.5.0-beta01 (`MaterialExpressiveTheme`, `MotionScheme.expressive()`) |
| Cor | Esquemas tonais a partir da spec MCU 2025 (índigo da marca + terciário acid-lime), claro / escuro / dusk / OLED, Material You opcional (`tools/gen-scheme.mjs`) |
| Ícones | Material Symbols Rounded como vetores gerados (`Sym.*`, `Sym.*Filled` para estados selecionados, `tools/gen-symbols.mjs`); nomes Font Awesome do config remoto mapeados por `faIcon()` |
| Formas | Polígonos `MaterialShapes`, `Morph` ao vivo via `MorphShape` / `MorphBlob`, morph de pressão em `ButtonDefaults.shapes()` |
| Progresso | `LoadingIndicator`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` |
| Imagens | Coil 3 (avatares DiceBear SVG solicitados como PNG via `pngAvatar`) |
| Rede | OkHttp 5 + kotlinx-serialization (`data/Net`) |
| Instalação privilegiada | Shizuku, Dhizuku (device owner), root, ADB sem fio integrado |
| Anúncios | Unity Ads (recompensado, `Rewarded_Android`) |

## Fluxo

```
AlphaStoreActivity  splash, edge-to-edge, ResultHost, permissão de notificação, deep links, taxa de atualização
  └─> AlphaStoreRoot(vm)                 ui/Root.kt      tema, gate, barra de navegação, host de abas, host de overlays
       ├─> routes = estado da VM          vm.tab, vm.selectedApp, vm.overlays
       ├─> CompositionLocals              LocalVm, LocalS (Settings), LocalD (DataState)
       └─> overlays = AlphaStoreSheet     ModalBottomSheet; LocalSheetClose para fechar com animação
MainViewModel  ui/MainViewModel.kt  orquestração (porta do App.tsx)
  ├─> Store.settings / Store.data         Persisted<T> (equivalentes de persist do zustand, AtomicFile JSON)
  ├─> Repository / CatalogProcessor       catálogo, config, mirrors, cache
  ├─> AppTracker                          porta de AppTrackerPlugin.java (instalar, desinstalar, scan de pacotes, segurança de rede)
  └─> Downloads / DownloadService         motor de download em foreground + notificações
```

## Mapa de features

| Origem web | Arquivo nativo | Status |
|---|---|---|
| App.tsx (orquestração, toasts, janitor, atualização forçada, deep links) | ui/MainViewModel.kt | Portado |
| Shell App.tsx, Header, BottomNav | ui/Root.kt | Portado (ShortNavigationBar, top app bar) |
| ModernAppList, ClassicAppList, AppCard, CompactAppCard, StoreFilters | ui/StoreScreen.kt | Portado |
| AppDetail, SelectedAppModalContainer | ui/Detail.kt | Portado |
| MyAppsView | ui/MyApps.kt | Portado |
| AboutView, LeaderboardModal, LeaderboardSubmitModal | ui/About.kt | Portado |
| SettingsModal, ScannerTestUI | ui/Settings.kt | Portado |
| ForcedStoreUpdateGate, MaintenanceMode | ui/Gate.kt | Portado |
| DinoGame, DinoGameModal | ui/Games.kt | Portado |
| PullToRefreshCharacter, Picker | ui/Pixel.kt, ui/PixelGrids.kt | Portado |
| ShizukuPowerModal, SentinelModal, DonationSheet | ui/Xtra.kt | Portado |
| NoticeModal, FAQModal, ReleaseNotesModal, Tutorial | ui/Sheets.kt | Portado |
| BundlePreviewModal, VirusTotalScanModal, AppChangeRequestModal | ui/Sheets.kt | Portado |
| AdDonationModal, ProfileStatsModal, CustomBundleModal, SubmissionModal | ui/Community.kt | Portado |
| utils/* (versionamento, links de imagem, assinaturas, submissão) | data/Logic.kt | Portado, coberto por LogicTest |
| store/* (zustand) | data/Stores.kt | Portado |
| Serviço VirusTotal | platform/VirusTotal.kt | Portado |

## Sistema de design

- Uma vitrine para todas as plataformas: `AppTile` com ícone primeiro (grades, faixas), `AppRow` (listas), `ShotCard`, pager `HeroCard` colorido pelo app, `Spotlight` numerado, categorias em blob, pacotes estilo pasta.
- Ícones e capturas são renderizados como a própria imagem (sem caixas de fundo); `Shot` mantém a proporção natural de cada captura.
- Movimento: `springClick`, entrada escalonada `reveal`, hand-off de ícone compartilhado card → detalhe (`LocalShared`), auto-avanço do hero com paralaxe, pop de ícone da nav, crossfade de imagem. "Desativar animações" troca por um MotionScheme instantâneo.
- Estado de instalação vive no ícone (`AppIcon(state = true)`): anel ondulado de download + badge recortado no canto; linhas adicionam `statusText()`.
- Listas de configurações, info de app e ações usam grupos segmentados (24dp externo, 4dp interno, gaps de 2dp); ícones de nível superior usam círculos tonais via `tonal()`.
- Pôsteres, cards de categoria e pacotes seguem cores container do esquema para acompanhar o Material You.

## Convenções

- Springs: specs espaciais para movimento e forma, specs de efeito para cor e alfa.
- Cantos na escala M3 (4, 8, 12, 16, 20, 28, full). `MaterialShapes` abstratos só em ícones, avatares e arte do hero.
- Tipo enfatizado só para manchetes, estados selecionados e ações primárias.
- Momentos hero: morph do blob do tutorial e card VICTORY da quest de anúncios.
- Botões de sheet fecham via `LocalSheetClose.current` para rodar a animação de esconder antes do `onDismiss`.

## Verificações

- `./gradlew :app:testDebugUnitTest --tests "*ShotsTest*" -Pshots=all` renderiza o app real com catálogo ao vivo sob Robolectric e grava PNGs em `app/build/shots` (`-Pshots=tall` para páginas longas). Só dev, precisa de rede.
- `./gradlew :app:testDebugUnitTest` roda `LogicTest` (versões, links de imagem, parsing de repo, corpos de issue de submissão e bundle, palavrões, níveis).
- `./gradlew :app:assembleDebug` gera o APK debug.

> A validação autoritativa de build/teste Android é via GitHub Actions.
