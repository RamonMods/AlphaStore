package com.ramonmods.alphastore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.ramonmods.alphastore.data.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One thing AlphaStore does. [go] jumps straight to it, when it has a home of its own. */
class Feature(val icon: ImageVector, val title: String, val body: String, val go: (MainViewModel.() -> Unit)? = null)

private class Chapter(val id: String, val title: String, val blurb: String, val icon: ImageVector, val color: Color, val shape: RoundedPolygon, val features: List<Feature>)

/** Every feature with its chapter, in reading order: AlphaStore AI answers "how do I..." from this and can open the right screen. */
val GUIDE: List<Pair<String, Feature>> by lazy { CHAPTERS.flatMap { c -> c.features.map { c.title to it } } }

private fun settings(page: String): MainViewModel.() -> Unit = { openSettings(page) }

/** Every feature in the app, chapter by chapter. Anything new that users can see or do gets a line here. */
private val CHAPTERS = listOf(
    Chapter("store", "A Loja", "Três catálogos, dois layouts e muitas formas de encontrar o app certo.", Sym.Apps, Color(0xFF6366F1), MaterialShapes.Cookie9Sided, listOf(
        Feature(Sym.Android, "Apps, PC e TV", "Catálogos separados para apps Android, softwares de PC e apps de TV, cada um com sua própria aba, busca, ordenação e filtros.", { navTo(Tab.ANDROID) }),
        Feature(Sym.ViewQuilt, "Layouts Moderno e Clássico", "O Moderno é uma vitrine com destaque, faixas e prateleiras. O Clássico é uma lista agrupada e limpa. Troque quando quiser em Configurações, Interface.", settings("interface")),
        Feature(Sym.Star, "Destaque principal", "Apps escolhidos a dedo deslizam no topo da página inicial moderna, usando as cores do próprio ícone com capturas de tela em leque. Uma captura ampla fica no banner do mesmo jeito que uma alta."),
        Feature(Sym.AutoAwesome, "Recomendados para você", "Um carrossel deslizável de escolhas diárias, além de um ranking numerado de apps selecionados."),
        Feature(Sym.NewReleases, "Novos e atualizados", "As adições mais recentes e os apps atualizados em sua própria faixa, com a lista completa a um toque de distância."),
        Feature(Sym.Category, "Prateleiras por categoria", "Blocos e faixas expressivas por categoria, como Mídia, Social, Privacidade e Utilitários, cada uma com a contagem de apps."),
        Feature(Sym.StarRate, "Mais populares e joias escondidas", "Dois rankings após os pacotes classificam apps pelas estrelas do GitHub: os mais estrelados e os menos estrelados que valem a pena. As estrelas vêm com o catálogo, então não custam requisições ao GitHub."),
        Feature(Sym.Layers, "Pacotes de apps", "Pastas selecionadas de apps que combinam entre si. Abra uma para ver todos, adicionar ou remover apps e instalá-los um por um."),
        Feature(Sym.LibraryAdd, "Seus próprios pacotes", "Crie um pacote em três passos: escolha apps, dê nome, cor e ícone, e depois mantenha local ou envie para todos.", { open(Overlay.CUSTOM_BUNDLE) }),
        Feature(Sym.AutoAwesome, "AlphaStore AI", "Toque na estrela e descreva o que precisa, como um app de música grátis. Use sua própria chave do Gemini, ChatGPT ou Claude, ou um modelo que roda no celular. Importe um modelo LiteRT deste celular ou cole um link do Hugging Face. Arraste a estrela para qualquer lugar.", { open(Overlay.AI) }),
        Feature(Sym.Search, "Busca", "A pílula de busca no topo encontra apps por nome, desenvolvedor, categoria ou descrição, e tolera acentos e erros de espaçamento."),
        Feature(Sym.SwapVert, "Ordenar e filtrar", "Ordene por mais novo, mais antigo, nome ou tamanho, e filtre a lista para uma única categoria com os botões de filtro."),
        Feature(Sym.Favorite, "Favoritos", "Pressione e segure qualquer card, deslize uma linha clássica ou toque no coração na página do app. O filtro de coração mostra apenas seus favoritos."),
        Feature(Sym.Description, "Prévias de “Sobre este app”", "As linhas clássicas mostram as primeiras linhas de cada descrição com Ver mais para ler o resto no lugar. Desative em Configurações, Interface.", settings("interface")),
        Feature(Sym.Sell, "Ícones de categoria", "Cada categoria tem seu próprio ícone nas linhas clássicas, para você escanear uma lista longa de relance."),
        Feature(Sym.SystemUpdate, "Painel de atualizações", "Quando há atualizações esperando, uma faixa fina na página inicial de Apps mostra quem está aguardando, com Atualizar tudo em um toque."),
        Feature(Sym.Campaign, "Anúncios", "Novidades da equipe AlphaStore aparecem em um megafone ao lado de Configurações no cabeçalho. Ele pulsa até você tocar em Entendi e some até chegar um novo anúncio. O último fica disponível no megafone no topo das Configurações. Ele pulsa até você ler."),
        Feature(Sym.AutoFixHigh, "Personagens ao puxar para atualizar", "Puxe para baixo para recarregar o catálogo com um gato pixelado, cachorro, coruja, fantasma e amigos, ou uma forma expressiva. Escolha o seu em AlphaStore Xtra.", settings("installer")),
        Feature(Sym.KeyboardArrowUp, "Voltar ao topo", "Uma seta flutuante aparece quando você rola bastante para baixo. Um toque e você volta ao topo."),
        Feature(Sym.AddCircleOutline, "Enviar um app", "Sugira um app do GitHub, GitLab ou Codeberg. A AlphaStore detecta nome, pacote, descrição, categoria, ícone e capturas de tela (dos metadados da loja ou do README), aceita exportações do Obtainium e abre uma issue pronta no GitHub.", { open(Overlay.SUBMISSION) }),
    )),
    Chapter("page", "Páginas de apps", "Tudo sobre um app e todas as formas de obtê-lo.", Sym.Info, Color(0xFF0EA5E9), MaterialShapes.Sunny, listOf(
        Feature(Sym.Download, "Instalação em um toque", "Baixe, instale, atualize ou abra um app pela página dele. O progresso envolve o ícone durante o download."),
        Feature(Sym.Image, "Capturas de tela", "Role pelas capturas de tela no formato real delas. Uma captura ampla fica no banner de destaque do mesmo jeito que uma alta. Toque em uma para ver em tela cheia."),
        Feature(Sym.History, "Versões e canais", "Escolha Estável, Beta, Alpha ou Nightly por app, ou selecione uma versão anterior específica. Instale uma Beta ou Nightly e as atualizações seguem esse canal, então ela não aparece mais como atualização da versão estável."),
        Feature(Sym.Memory, "A build certa para seu celular", "A AlphaStore escolhe o APK que combina com o processador do seu celular e deixa você escolher outra arquitetura."),
        Feature(Sym.Star, "Estrelas do GitHub", "Apps hospedados no GitHub mostram a contagem de estrelas logo abaixo do nome do desenvolvedor."),
        Feature(Sym.Download, "Contagem de downloads", "Quando a fonte informa quantas vezes os arquivos de versão foram baixados, essa contagem aparece junto às estrelas abaixo do nome do desenvolvedor."),
        Feature(Sym.AccountTree, "Forks", "Quando o repositório de um app é um fork, Origem e segurança mostra o projeto original e suas estrelas, a um toque de distância."),
        Feature(Sym.Science, "Inspecionar pacote", "Toque em Inspecionar na linha de estatísticas para ver rastreadores, permissões, bibliotecas, compatibilidade e assinatura antes de instalar. Quando a AlphaStore precisa baixar a build para lê-la, o modo Apenas Wi-Fi pergunta antes, igual ao Download. O relatório fica até uma versão mais nova ser oferecida ou o link de origem mudar. Instalar depois usa o mesmo download.", settings("installer")),
        Feature(Sym.Info, "Informações do app", "Desenvolvedor, tamanho, nome do pacote e origem, recolhidos até você querer ver."),
        Feature(Sym.NewReleases, "Novidades", "As notas da versão que você baixaria, recolhidas em uma linha abaixo de Sobre. Marcadores Markdown são removidos, então títulos e listas ficam em texto simples. A linha aparece na hora e as notas carregam em segundo plano, inclusive quando há atualização esperando."),
        Feature(Sym.Extension, "Patches", "Builds modificadas listam cada patch aplicado em uma linha recolhível."),
        Feature(Sym.Delete, "Desinstalar", "Remova um app instalado pela página dele ou por Meus Apps sem sair da AlphaStore. Silencioso com root, Shizuku ou Dhizuku."),
        Feature(Sym.Policy, "Verificação no VirusTotal", "Verifique um download, um APK instalado ou o arquivo de versão em mais de 70 mecanismos antivírus com sua própria chave gratuita."),
        Feature(Sym.Code, "Origem e segurança", "Vá para o código-fonte, a página oficial de versões ou o site do desenvolvedor em um toque."),
        Feature(Sym.NotificationsOff, "Ignorar atualizações", "Oculte uma atualização por uma semana, até a próxima versão ou para sempre. Atualizações ignoradas ficam em Meus Apps com um botão Reativar."),
        Feature(Sym.VisibilityOff, "Não gerenciar um app", "Mantenha um app que você atualiza em outro lugar fora de Meus Apps e das atualizações pelo menu ⋮, ou ignore todos os apps de outras lojas nas Configurações. Traga-os de volta em Não gerenciados."),
        Feature(Sym.Person, "Página do desenvolvedor", "Toque no nome do desenvolvedor na página de um app para ver todos os apps dele na AlphaStore."),
        Feature(Sym.SaveAlt, "Exportar e baixar novamente", "Salve o APK na pasta Downloads, baixe de novo ou exclua uma cópia salva para liberar espaço."),
        Feature(Sym.Share, "Compartilhar links e selos", "Compartilhe um link curto da web (ramonmods.github.io/AlphaStore-Data/app/?id=...) que abre o app na AlphaStore, um link direto ou um selo Markdown ou HTML para seu README."),
        Feature(Sym.EditNote, "Solicitar alteração", "Notou um ícone errado, link quebrado ou descrição antiga? Envie uma correção para exatamente esse campo."),
        Feature(Sym.Report, "Relatar um problema", "Download quebrado? Relate pela página do app e os detalhes já vêm preenchidos."),
        Feature(Sym.Extension, "Ajuda do MicroG", "Apps que precisam do MicroG avisam, com um link para instalar e um guia para saber mais."),
        Feature(Sym.Link, "Links diretos", "Links alphastore:// e Detalhes do app na loja do Android abrem a página certa na AlphaStore."),
    )),
    Chapter("library", "Meus apps e downloads", "Sua biblioteca, sua fila e atualizações que se cuidam sozinhas.", Sym.GridView, Color(0xFF22C55E), MaterialShapes.Clover4Leaf, listOf(
        Feature(Sym.Inventory2, "Biblioteca", "Todos os apps que você baixou ou instalou pela AlphaStore, filtrados por Todos, Instalados, Baixados e Atualizações, com busca própria.", { navTo(Tab.MYAPPS) }),
        Feature(Sym.Delete, "Desinstalar vários apps", "Pressione e segure um app instalado em Meus Apps para começar a selecionar, toque em mais (ou Todos) e depois em Desinstalar. Silencioso com root, Shizuku ou Dhizuku; caso contrário, um aviso do sistema por app."),
        Feature(Sym.Downloading, "Fila de downloads", "Downloads ativos, instalações prontas e atualizações pendentes em uma folha compacta sobre o que você estiver fazendo, com Instalar tudo, Atualizar tudo e Limpar fila. Enquanto algo baixa, um anel de progresso no cabeçalho abre a fila.", settings("queue")),
        Feature(Sym.Notifications, "Downloads em segundo plano", "Os downloads continuam quando você sai da AlphaStore, com progresso ao vivo nas notificações e um toque para instalar quando terminarem."),
        Feature(Sym.Autorenew, "Atualização automática", "A AlphaStore verifica no cronograma que você escolher, de a cada 15 minutos até uma vez por semana, mesmo fechada e após reiniciar. Ela baixa atualizações para apps instalados que gerencia: os de Meus Apps e os apps do Orbit que não estão pausados nem definidos apenas para acompanhar. Quando um app do Orbit também está na biblioteca, a cópia da biblioteca é atualizada. Pular atualização automática fica no menu da página do app, ao lado de Não gerenciar. Atualizações ignoradas também ficam de fora. As fontes do Orbit ainda são verificadas nesse cronograma.", settings("network")),
        Feature(Sym.SystemUpdate, "Instalar automaticamente", "Deixe a AlphaStore instalar as atualizações que baixou, esteja ela aberta ou fechada. Tudo que não puder ser concluído é informado na próxima vez que você abrir o app.", settings("network")),
        Feature(Sym.Verified, "Apenas versões estáveis", "Ignora builds Beta, Alpha e Nightly sempre que existir uma versão estável.", settings("network")),
        Feature(Sym.Wifi, "Apenas Wi-Fi", "Mantém fora dos dados móveis os downloads que você inicia. Nos dados móveis, a AlphaStore pergunta antes de baixar aquele app mesmo assim. Atualização automática apenas no Wi-Fi é separado: atualizações em segundo plano esperam pelo Wi-Fi mesmo quando outros downloads são permitidos nos dados móveis.", settings("network")),
        Feature(Sym.CleaningServices, "Zelador", "APKs sobrando são limpos uma semana após a instalação, ou na hora com Limpeza automática ligada. Uma pílula oferece limpeza em um toque quando os arquivos acumulam.", settings("storage")),
    )),
    Chapter("orbit", "Orbit", "Qualquer app, direto da fonte, instalado e atualizado pela AlphaStore.", Sym.Orbit, Color(0xFF14B8A6), MaterialShapes.Cookie9Sided, listOf(
        Feature(Sym.AddLink, "Adicionar qualquer app por link", "Cole um link do GitHub, GitLab, Codeberg, SourceHut, F-Droid, IzzyOnDroid, Uptodown, APKMirror, APKPure, Aptoide, itch.io, Farsroid, LiteAPKs, APK4Free, CoolApk, SourceForge, Jenkins, Telegram ou Discord, um link direto de APK, XAPK ou APKM, ou qualquer página da web que os ofereça. A AlphaStore lê nome, ícone, descrição, capturas de tela e versões.", { navTo(Tab.ORBIT) }),
        Feature(Sym.WarningAmber, "Não permitido no Orbit", "Apps ReVanced e Metrolist não são permitidos no Orbit. MicroG RE é permitido, porque é um projeto Morphe. Siga Morphe em vez de ReVanced, e ArchiveTune ou BiTChord em vez de Metrolist.", { navTo(Tab.ORBIT) }),
        Feature(Sym.VisibilityOff, "Ocultar Orbit", "Na primeira vez que você abre o Orbit, pode ocultá-lo se quiser apenas os apps próprios da AlphaStore. Reative em Configurações, Interface, Abas de navegação."),
        Feature(Sym.SystemUpdate, "Instalar e atualizar em um toque", "Apps do Orbit instalam, atualizam e se atualizam automaticamente como apps da loja, com todas as rotas de instalação da AlphaStore: root, Shizuku, Dhizuku e depuração sem fio.", { navTo(Tab.ORBIT) }),
        Feature(Sym.History, "Qualquer versão", "Cada versão recente de um app do Orbit aparece no seletor de versões, incluindo builds Nightly, então voltar para uma build anterior é um toque. Depois de instalar uma Nightly, as atualizações seguintes ficam nesse canal."),
        Feature(Sym.Tune, "Ajustes finos", "Escolha qual APK conta com um filtro por nome de arquivo, inclua pré-lançamentos, leia a versão da tag com um padrão, espere antes de oferecer uma nova versão e defina ícone e capturas de tela você mesmo."),
        Feature(Sym.Visibility, "Apenas acompanhar", "Acompanhe novas versões sem Instalar ou Atualizar. Ative em Ajustes finos ou pelo menu de toque longo. Uma exportação do Obtainium mantém a opção nos dois sentidos.", { navTo(Tab.ORBIT) }),
        Feature(Sym.Schedule, "Esperar antes de oferecer", "Segure uma nova versão do Orbit por 1, 2, 3 ou 7 dias, nas Configurações ou em um app. A versão mais nova que já tem idade suficiente é a oferecida. A idade mínima de atualização do Obtainium é importada e exportada.", settings("network")),
        Feature(Sym.Key, "Tokens de origem", "Tokens na aba Orbit permitem que GitHub, GitLab e Codeberg acessem repositórios privados, baixem seus arquivos de versão e evitem o limite público de taxa. Cada campo leva à página que cria o token. O token do GitHub é o mesmo das Ferramentas de desenvolvedor, então um token já salvo é usado.", { navTo(Tab.ORBIT) }),
        Feature(Sym.Sell, "Tags", "Dê tags a um app do Orbit em Ajustes finos. Todos, Fontes, Tags e o botão i ficam em uma linha. Fontes e Tags abrem uma caixa onde você pode escolher mais de uma e tocar em Concluído. O botão i lista todos os links que o Orbit consegue ler.", { navTo(Tab.ORBIT) }),
        Feature(Sym.TravelExplore, "Descobrir", "O diretório de apps do Obtainium fica dentro do Orbit: pesquise centenas de configurações prontas e adicione uma com um toque.", { navTo(Tab.ORBIT) }),
        Feature(Sym.FileDownload, "Importar e exportar Obtainium", "Traga seu arquivo de exportação do Obtainium, JSON ou links obtainium://, e leve seu Orbit de volta no formato do Obtainium. Links Adicionar ao Obtainium também abrem na AlphaStore."),
        Feature(Sym.Publish, "Compartilhar com a biblioteca da AlphaStore", "Pressione e segure um app do Orbit e compartilhe: abre um envio pronto para que todos encontrem o app, e conta como contribuição com XP. Um app compartilhado mantém uma marca de biblioteca. A cada cinco apps adicionados, a AlphaStore pergunta de novo. Esse lembrete fica até você tocar em Compartilhar um ou Depois."),
        Feature(Sym.Bookmark, "Já na biblioteca", "Adicionar um app que o Orbit já tem na loja mantém os dois. Um marcador no card do Orbit abre a página da biblioteca. O celular também mostra o app como instalado no Orbit, então Baixar não é oferecido para uma cópia que já está lá."),
        Feature(Sym.NotificationsOff, "Pausar atualizações", "Mantenha um app do Orbit na versão que ele tem pelo menu de toque longo."),
    )),
    Chapter("install", "Instalação do seu jeito", "De instalações em um toque a atualizações totalmente silenciosas.", Sym.InstallMobile, Color(0xFFF97316), MaterialShapes.Gem, listOf(
        Feature(Sym.Android, "Escolha seu instalador", "Use o instalador do sistema, escolha um toda vez ou sempre entregue APKs a um app instalador específico.", settings("installer")),
        Feature(Sym.Speed, "Pule a espera de 24 horas", "A depuração sem fio integrada instala apps instantaneamente como usuário shell, configurada uma vez com alguns passos guiados. A AlphaStore reativa a Depuração sem fio sozinha quando ela está desligada.", settings("installer")),
        Feature(Sym.InstallMobile, "Instale qualquer APK", "A AlphaStore também é instalador: abra um APK, XAPK, APKM, APKS ou um zip de APKs no seu gerenciador de arquivos ou navegador e escolha Instalar com AlphaStore, ou use Instalar um arquivo APK nas Configurações. Um pacote é aberto pelos APKs dentro dele, mesmo quando o arquivo em si não é um APK. Uma folha mostra o app, a versão e o que está instalado, pode verificar o arquivo com sua chave do VirusTotal e instala via root, Shizuku, Dhizuku ou depuração sem fio, pulando a espera de 24 horas. Pacotes divididos mantêm apenas as partes compatíveis com este celular.", settings("installer")),
        Feature(Sym.Science, "Por dentro deste APK", "A folha do instalador de APK lê o arquivo antes de instalar: o framework usado (Flutter, React Native, Unity, Compose e mais), suporte a processadores e se roda neste celular, prontidão para páginas de 16 KB, rastreadores encontrados no código, permissões com as adicionadas em atualização marcadas como Novas, componentes, níveis de SDK, bibliotecas nativas, para onde vai o tamanho e a chave de assinatura. Toque no card para a página completa. Tudo é lido offline.", settings("installer")),
        Feature(Sym.Tune, "Opções na folha do APK", "Além da instalação tem sua própria chave na folha do instalador de APK, via root, Shizuku ou depuração sem fio. Chaves que o APK precisa, como Permitir downgrade para uma versão mais antiga ou Ignorar bloqueio de SDK alvo para um app antigo, são escolhidas para você e marcadas como Necessárias.", settings("installer")),
        Feature(Sym.Shield, "Proteção contra sideloading", "Quando o Android ativa seu componente de verificação de desenvolvedor, que pode atrasar ou bloquear apps instalados por fora da loja, a AlphaStore oferece Proteger-me: um toque desativa isso via root, Shizuku ou depuração sem fio. Sem nenhum deles, o popup guia você pela configuração da depuração sem fio e volta para concluir. A AlphaStore verifica o celular depois e diz exatamente por que, se o Android recusou.", settings("installer")),
        Feature(Sym.GppGood, "Escudo de proteção", "Um escudo na barra de busca (o cabeçalho na aba Dev) mostra o estado de relance: um escudo riscado enquanto a verificação está ligada, um com visto quando a AlphaStore desativou. Toque a qualquer momento para proteger o celular ou reverter tudo em um toque. Oculte com Escudo de proteção em AlphaStore Xtra.", settings("installer")),
        Feature(Sym.Bolt, "Instalações silenciosas com Shizuku", "Com Shizuku ou Shizuku-Next em execução, instalações e atualizações passam sem aviso a cada vez. Shizuku-Next também aparece na lista de instaladores.", settings("installer")),
        Feature(Sym.AdminPanelSettings, "Dhizuku", "Instalações, atualizações e desinstalações silenciosas pelo device owner compartilhado do Dhizuku, sem Shizuku nem root. Android 8 ou superior.", settings("installer")),
        Feature(Sym.Tune, "Além da instalação", "Antes de cada instalação via root ou Shizuku, ou na folha do instalador de APK também via depuração sem fio, escolha entre 19 opções: downgrade, conceder todas as permissões, configurações restritas, builds de teste, local de armazenamento, Play Store ou instalador personalizado e mais.", settings("installer")),
        Feature(Sym.Terminal, "Modo root", "Instalações silenciosas, atualizações e todas as ferramentas do Guardian via root, sem precisar de Shizuku ou depuração sem fio.", settings("installer")),
        Feature(Sym.Sync, "Adotar apps instalados", "Com root, a AlphaStore assume as atualizações de apps do catálogo que você obteve em outro lugar. Seus dados ficam exatamente como estavam.", settings("installer")),
        Feature(Sym.Lock, "Bloqueio de atualização", "No Android 14 ou mais novo com root, outras lojas precisam pedir sua permissão antes de atualizar apps instalados pela AlphaStore.", settings("installer")),
        Feature(Sym.DeleteSweep, "Limpar cache de todos os apps", "Com root, libere espaço em todos os seus apps em um único toque.", settings("installer")),
        Feature(Sym.Refresh, "Forçar nova varredura", "Recarregue a lista de apps instalados sempre que algo parecer desatualizado.", settings("installer")),
    )),
    Chapter("xtra", "Guardian e Sentinel", "Ferramentas de segurança e controle integradas.", Sym.Shield, Color(0xFFEF4444), MaterialShapes.Cookie6Sided, listOf(
        Feature(Sym.Shield, "Guardian: permissões", "Identifique apps com permissões sensíveis como localização, microfone, contatos e armazenamento, e revogue-as.", settings("installer")),
        Feature(Sym.Unarchive, "Guardian: extrair APK", "Extraia o APK de qualquer app instalado e salve-o.", settings("installer")),
        Feature(Sym.DeleteForever, "Guardian: debloat", "Remova apps de sistema pré-instalados de forma reversível, guiado por uma lista da comunidade que marca o que é seguro remover. As notas de cada app trazem links e fontes tocáveis.", settings("installer")),
        Feature(Sym.MonitorHeart, "Sentinel: varreduras rápida e profunda", "Verifique apps instalados contra um banco de assinaturas de ameaças e varra seu armazenamento em busca de arquivos infectados.", settings("installer")),
        Feature(Sym.Lan, "Sentinel: sentinela de rede", "Uma auditoria ao vivo da sua conexão: VPN, DNS, proxies, portais cativos e criptografia do Wi-Fi.", settings("installer")),
        Feature(Sym.DeveloperMode, "Vigia ADB", "Veja de relance se a ponte de desenvolvedor do seu celular está aberta."),
    )),
    Chapter("look", "Aparência e sensação", "Faça a AlphaStore ter a aparência e o movimento que você gosta.", Sym.Palette, Color(0xFFA855F7), MaterialShapes.Flower, listOf(
        Feature(Sym.LightMode, "Sistema, Claro, Crepúsculo, Escuro e OLED", "Cinco temas. Sistema segue a configuração clara ou escura do celular. O botão na pílula de busca alterna entre eles de qualquer lugar com uma revelação circular.", settings("visuals")),
        Feature(Sym.Palette, "Paletas de cores", "Quatorze paletas, de AlphaStore a Cocoa, cada uma ajustada para todos os temas.", settings("visuals")),
        Feature(Sym.FormatColorFill, "Crie sua própria paleta", "Toque em Criar na linha de paletas, escolha uma cor primária e, opcionalmente, secundária e terciária, selecione Suave, Equilibrado ou Vibrante e visualize em todos os temas. Pressione e segure sua paleta para editá-la.", settings("visuals")),
        Feature(Sym.AutoAwesome, "Material You", "Use as cores do seu papel de parede no Android 12 ou mais recente.", settings("visuals")),
        Feature(Sym.FontDownload, "Fontes", "Onze fontes integradas, ou importe seu próprio arquivo TTF, OTF ou WOFF2.", settings("visuals")),
        Feature(Sym.Compress, "Modo compacto", "Blocos menores, linhas mais finas e uma coluna extra, para caber mais em cada tela.", settings("visuals")),
        Feature(Sym.BlurOn, "Dock de vidro", "Um dock de navegação flutuante e levemente translúcido que desliza para fora enquanto você rola para baixo.", settings("visuals")),
        Feature(Sym.ViewQuilt, "Navegação em tela grande", "Em tablets, dobráveis, TVs e telas largas divididas, mantenha a barra lateral ou mova o dock para baixo ou para cima. Celulares sempre mantêm o dock embaixo.", settings("visuals")),
        Feature(Sym.ViewAgenda, "Estilos do dock", "Pílula, Clássico, Ícones, Rotulado, Expressivo e Shape shift. Expressivo e Shape shift desbloqueiam com anúncios do Fuel the Code.", settings("visuals")),
        Feature(Sym.Palette, "Ícones do app", "Seis ícones de launcher além do clássico. Neon e Gold desbloqueiam com anúncios do Fuel the Code: toque em um e cada anúncio assistido conta para ele.", settings("visuals")),
        Feature(Sym.OpenInFull, "Tamanho do dock", "Ajuste o dock entre compacto e grande com uma prévia ao vivo. Começa compacto.", settings("visuals")),
        Feature(Sym.TouchApp, "Resposta tátil", "Toques táteis em toques, chaves e sucessos.", settings("visuals")),
        Feature(Sym.Speed, "Movimento suave", "Prefira a maior taxa de atualização que sua tela oferece.", settings("visuals")),
        Feature(Sym.Bolt, "Modo de desempenho", "Lite mantém celulares antigos fluidos ao parar movimentos decorativos; Boost mantém mais imagens prontas nos rápidos. Auto escolhe por você.", settings("visuals")),
        Feature(Sym.MotionPhotosOff, "Desativar animações", "Toda transição acontece instantaneamente.", settings("visuals")),
        Feature(Sym.ViewAgenda, "Ocultar abas", "Oculte a aba Apps, PC, TV ou Meus Apps. A aba Dev sempre fica.", settings("interface")),
        Feature(Sym.OpenInFull, "Tela cheia", "Oculte a barra de status enquanto a AlphaStore estiver aberta, ou mantenha visível. Deslize de cima para baixo para espiar.", settings("visuals")),
        Feature(Sym.Tv, "Android TV (Beta)", "A AlphaStore roda no Android TV: abre no catálogo de TV, instala apps de TV direto na TV e cada card acende conforme o controle se move. Toque na pílula TV Beta para relatar um bug. Downloads que travam em um servidor mudam para uma conexão simples sozinhos."),
        Feature(Sym.DesktopWindows, "Tablets e paisagem", "A AlphaStore gira livremente em tablets e dobráveis, com uma barra lateral e uma coluna centralizada e legível."),
    )),
    Chapter("settings", "Configurações e backup", "Configurações do seu jeito e progresso que nunca se perde.", Sym.Settings, Color(0xFFEC4899), MaterialShapes.Pentagon, listOf(
        Feature(Sym.ToggleOn, "Atalhos rápidos", "Fixe as chaves que você mais usa no topo das Configurações e arraste-as na ordem que quiser.", { openSettings() }),
        Feature(Sym.DragHandle, "Organizar o hub de Configurações", "Pressione e segure um card de configuração e arraste para um novo lugar. Redefinir traz a ordem original de volta.", { openSettings() }),
        Feature(Sym.Backup, "Backup de identidade", "Salve seu perfil, XP, insígnias, favoritos e configurações em um arquivo .osf assinado e restaure em qualquer celular.", settings("identity")),
        Feature(Sym.Cloud, "Backup automático na nuvem", "Conecte Google Drive, OneDrive, Proton Drive ou qualquer app de armazenamento, e a AlphaStore mantém seu backup atualizado diariamente, semanalmente ou mensalmente.", settings("identity")),
        Feature(Sym.CloudOff, "Sem servidor por design", "Sem conta AlphaStore e sem servidor AlphaStore. Seus dados ficam no seu celular e na nuvem que você escolher."),
        Feature(Sym.VerifiedUser, "Verificações de integridade", "Backups com progresso impossível são recusados, então cada nível e insígnia foi conquistado de verdade."),
    )),
    Chapter("profile", "Perfil, XP e insígnias", "Cada app, anúncio e partida faz você subir de patente.", Sym.EmojiEvents, Color(0xFFEAB308), MaterialShapes.Cookie12Sided, listOf(
        Feature(Sym.Badge, "Cartão de piloto", "Um ID holográfico que inclina com seu celular e sob seu dedo. Toque para virá-lo.", { openProfileStats() }),
        Feature(Sym.Draw, "Assinatura", "Assine o verso do seu cartão de piloto em seis tintas, com desfazer e refazer para cada traço."),
        Feature(Sym.Person, "Apelido e avatar", "Escolha um apelido alienígena e um avatar. Toque no avatar do seu cartão para mudar quando quiser.", { openProfileStats() }),
        Feature(Sym.Bolt, "XP", "Ganhe 10 XP por cada app na sua biblioteca, 100 por cada app enviado, 40 por cada missão do Fuel the Code, 150 por cada insígnia de coleção, além do XP do minijogo."),
        Feature(Sym.TrendingUp, "Níveis e patentes", "Cada nível exige um pouco mais de XP que o anterior. Suba de Cadete para Bronze no 10, Prata no 30 e Ouro no 50."),
        Feature(Sym.Diamond, "Skins metálicas do cartão", "Pilotos Bronze, Prata e Ouro recebem um cartão nesse metal, com a patente na frente."),
        Feature(Sym.Category, "Insígnias de coleção", "Desbloqueie os dois ícones bloqueados para Icon Collector e os dois docks bloqueados para Dock Master."),
        Feature(Sym.MilitaryTech, "Arquivo de insígnias", "Todas as insígnias que você pode ganhar, como consegui-las e o quão perto você está. Destaque três delas no seu perfil e na aba Dev.", { profileClick("badges", 0) }),
        Feature(Sym.QueryStats, "Estatísticas", "Favoritos, downloads, XP e patente, contando toda vez que você abre seu perfil.", { openProfileStats() }),
    )),
    Chapter("play", "Jogar e competir", "Missões, um minijogo e um Hall da Fama global.", Sym.SportsEsports, Color(0xFF14B8A6), MaterialShapes.SoftBurst, listOf(
        Feature(Sym.Swords, "Fuel the Code", "Uma missão opcional em dois capítulos. Cada anúncio assistido até o fim apoia a AlphaStore, rende XP e leva você ao próximo chefe e à insígnia dele.", { open(Overlay.AD_DONATION) }),
        Feature(Sym.Castle, "Chefes e insígnias", "Derrote o Golem do Código, a Rainha do Enxame de Bugs, a Hidra dos Conflitos de Merge e mais, cada um deixando sua própria insígnia."),
        Feature(Sym.Egg, "AlphaStore Run", "Um runner do Dino que faz companhia enquanto a loja está em manutenção, valendo até 300 XP por dia e três insígnias Dino."),
        Feature(Sym.EmojiEvents, "Hall da Fama", "Toque na moeda do desenvolvedor na aba Dev e toque de novo para abrir o ranking global. Reivindique sua patente quando tiver cinco contribuições; depois disso, sua entrada se atualiza sozinha quando você sobe de nível.", { navTo(Tab.ABOUT) }),
        Feature(Sym.Gavel, "Jogo limpo", "Limites diários nas missões e no minijogo, e verificações de integridade em cada reivindicação do ranking, mantêm a disputa justa."),
    )),
    Chapter("dev", "A aba Dev e além", "Ajuda, suporte e algumas coisas escondidas de propósito.", Sym.Code, Color(0xFF64748B), MaterialShapes.Pill, listOf(
        Feature(Sym.MenuBook, "Conheça a AlphaStore", "Esta página. Todos os recursos em um só lugar, pelo botão de livro no topo da aba Dev."),
        Feature(Sym.HelpOutline, "Perguntas frequentes", "Respostas sobre segurança, builds ReVanced, instalações com falha, rastreadores e mais.", { open(Overlay.FAQ) }),
        Feature(Sym.NewReleases, "Novidades", "Notas de versão direto dos releases da AlphaStore no GitHub. Marcadores Markdown são removidos, e as notas completas ficam a um toque de distância.", { open(Overlay.RELEASE_NOTES) }),
        Feature(Sym.Forum, "Conecte-se", "GitHub, X, Discord e o site da AlphaStore, direto na aba Dev.", { navTo(Tab.ABOUT) }),
        Feature(Sym.Coffee, "Apoie a AlphaStore", "Pague um café para o desenvolvedor pelo Ko-fi, UPI ou PayPal.", { navTo(Tab.ABOUT) }),
        Feature(Sym.Public, "Espelhos e offline", "O catálogo carrega do GitHub, JSDelivr ou GitHack, o que responder primeiro, mesmo quando sua rede ou DNS bloqueia o GitHub, e fica em cache para uso offline."),
        Feature(Sym.Construction, "Manutenção e atualizações da loja", "A AlphaStore avisa quando a loja está em manutenção e verifica, escaneia e instala suas próprias atualizações."),
        Feature(Sym.Terminal, "Opções de desenvolvedor", "Toque no logo da AlphaStore sete vezes para desbloquear ferramentas de teste de catálogos, configurações e espelhos."),
        Feature(Sym.QuestionMark, "Segredos", "Alguns easter eggs e insígnias secretas estão escondidos pelo app. O FAQ tem um enigma para você começar."),
    )),
)

/** AlphaStore hides the status bar, so the plain insets read zero and the floating chips slid under the camera cutout. Its slot (and any cutout) is kept clear instead. */
private val GuideTop @Composable get() = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/** Know AlphaStore: the whole app as an expressive, searchable field guide. */
@Composable
fun KnowAlphaStoreScreen(vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val cs = MaterialTheme.colorScheme; val list = rememberLazyListState(); val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = remember(q) {
        if (q.isEmpty()) CHAPTERS else CHAPTERS.map { c -> Chapter(c.id, c.title, c.blurb, c.icon, c.color, c.shape, c.features.filter { q in it.title.lowercase() || q in it.body.lowercase() }) }
            .filter { it.features.isNotEmpty() }
    }
    val total = remember { CHAPTERS.sumOf { it.features.size } }
    fun go(f: Feature) { vm.haptic(H.SELECTION); onClose(); scope.launch { delay(120); f.go?.invoke(vm) } }
    // Hero, search and chips come first; each chapter is a header plus its rows
    val starts = remember(shown) { shown.runningFold(3) { at, c -> at + c.features.size + 1 }.dropLast(1) }
    val current by remember(starts) { derivedStateOf { if (list.firstVisibleItemIndex < 3) -1 else starts.indexOfLast { it <= list.firstVisibleItemIndex + 1 } } }
    val chips = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Rows land just below the floating chip bar instead of under it
    val pin = GuideTop.getTop(density) + with(density) { 84.dp.roundToPx() }
    fun jump(ci: Int) {
        // Far chapters snap straight there: an animated fling across dozens of rows composes every one of them on the way
        val at = starts[ci]
        scope.launch { if (kotlin.math.abs(at - list.firstVisibleItemIndex) <= 8) list.animateScrollToItem(at, -pin) else list.scrollToItem(at, -pin) }
    }
    val floating by remember { derivedStateOf { list.firstVisibleItemIndex >= 3 } }
    LaunchedEffect(current) { if (current >= 0) { if (floating) chips.animateScrollToItem(current) else chips.requestScrollToItem(current) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = readableGutter(maxWidth)
        LazyColumn(Modifier.fillMaxSize().background(cs.surface), list, contentPadding = PaddingValues(start = gutter, end = gutter, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        item("hero") { Box(Modifier.bleed(gutter)) { GuideHero(total, gutter) } }
        item("search") { GuideSearch(query) { query = it } }
        item("chips") { ChapterChips(shown, -1, rememberLazyListState(), ::jump) }
        if (shown.isEmpty()) item("empty") { EmptyState(Sym.SearchOff, "Nada corresponde a “$query”", "Tente uma palavra mais curta, como backup ou tema.") }
        shown.forEach { c ->
            item("h-${c.id}", contentType = "header") { ChapterHeader(c, CHAPTERS.indexOfFirst { it.id == c.id }) }
            itemsIndexed(c.features, key = { _, f -> "${c.id}-${f.title}" }, contentType = { _, _ -> "feature" }) { i, f -> FeatureRow(c, f, i, c.features.size) { go(f) } }
        }
        item("end") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).background(cs.primaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
                    Icon(painterResource(com.ramonmods.alphastore.R.drawable.ic_splash_logo), null, Modifier.size(40.dp), tint = cs.onPrimaryContainer)
                }
                Text("Isso é tudo da AlphaStore", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLargeEmphasized)
                Text("$total recursos em ${CHAPTERS.size} capítulos, e mais a caminho.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                Surface(Modifier.padding(top = 14.dp), shape = Pill, color = cs.surfaceContainerHigh) {
                    Text("v$STORE_VERSION", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    // Past the top, chapters float in a rounded bar under the status bar, marking where you are
    androidx.compose.animation.AnimatedVisibility(floating, Modifier.align(Alignment.TopCenter).windowInsetsPadding(GuideTop).padding(horizontal = 12.dp, vertical = 8.dp),
        enter = androidx.compose.animation.slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut()) {
        Surface(Modifier.widthIn(max = 840.dp), shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerHigh, shadowElevation = 6.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ scope.launch { list.toTop() } }, Modifier.padding(start = 4.dp)) { Icon(Sym.KeyboardArrowUp, "Voltar ao topo") }
                ChapterChips(shown, current, chips, ::jump, start = 0.dp)
            }
        }
    }
    }
}

@Composable
private fun ChapterChips(shown: List<Chapter>, current: Int, state: androidx.compose.foundation.lazy.LazyListState, onJump: (Int) -> Unit, start: Dp = 16.dp) =
    LazyRow(Modifier.fillMaxWidth(), state, contentPadding = PaddingValues(start = start, end = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(shown, key = { _, c -> c.id }) { ci, c -> Choice(ci == current, c.title, c.icon) { onJump(ci) } }
    }

private val HERO_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst)

@Composable
private fun GuideHero(total: Int, gutter: Dp) {
    val cs = MaterialTheme.colorScheme; val still = LocalS.current.calm
    var shape by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) { if (!still) while (true) { delay(2600); shape++ } }
    Box(Modifier.fillMaxWidth().clipToBounds().background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface)))) {
        Turning(MaterialShapes.Cookie12Sided, cs.primary.copy(.08f), 280.dp, Modifier.align(Alignment.TopEnd).offset(90.dp, (-40).dp), 50_000)
        Column(Modifier.windowInsetsPadding(GuideTop).padding(horizontal = 20.dp + gutter).padding(top = 8.dp, bottom = 12.dp)) {
            MorphBlob(HERO_SHAPES, shape, 104.dp, cs.primary, Modifier.padding(top = 12.dp)) {
                Icon(painterResource(com.ramonmods.alphastore.R.drawable.ic_splash_logo), null, Modifier.size(72.dp), tint = cs.onPrimary)
            }
            Text("GUIA DE CAMPO", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.labelLargeEmphasized, color = cs.primary)
            Text("Conheça a AlphaStore", style = MaterialTheme.typography.displayMediumEmphasized)
            Text("Todos os $total recursos da AlphaStore, de backups e modo compacto a insígnias, Hall da Fama e Dino Run. Toque em um recurso com seta para ir direto a ele.",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideSearch(query: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(56.dp).clip(Pill).background(cs.surfaceContainerHigh).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Sym.Search, null, tint = cs.onSurfaceVariant)
        BasicTextField(query, onChange, Modifier.weight(1f).padding(horizontal = 12.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
            decorationBox = { field -> Box { if (query.isEmpty()) Text("Buscar recursos", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant); field() } })
        if (query.isNotEmpty()) IconButton({ onChange("") }) { Icon(Sym.Close, "Limpar busca") }
    }
}

@Composable
private fun ChapterHeader(c: Chapter, index: Int) {
    val (bg, fg) = tonal(c.color)
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp).fillMaxWidth().background(bg, RoundedCornerShape(32.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Drawn shapes, not clipped layers or endless spins: cheap to bring on screen when jumping between chapters
        Box(Modifier.size(64.dp).background(fg.copy(.16f), c.shape.toShape()), contentAlignment = Alignment.Center) {
            Icon(c.icon, null, Modifier.size(30.dp), tint = fg)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("CAPÍTULO ${index + 1}", style = MaterialTheme.typography.labelMedium, color = fg.copy(.75f))
            Text(c.title, style = MaterialTheme.typography.headlineSmallEmphasized, color = fg)
            Text(c.blurb, style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.background(fg, Pill).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("${c.features.size}", style = MaterialTheme.typography.labelLargeEmphasized, color = bg) }
    }
}

/** One feature as a plain segmented row: a much lighter build than the interactive list item, so long jumps stay smooth. */
@Composable
private fun FeatureRow(c: Chapter, f: Feature, i: Int, n: Int, onGo: () -> Unit) {
    val (bg, fg) = tonal(c.color); val cs = MaterialTheme.colorScheme
    val shape = colShape(i, n)
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 2.dp).fillMaxWidth().clip(shape).background(cs.surfaceContainer)
        .then(if (f.go != null) Modifier.clickable(onClick = onGo) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(bg, MaterialShapes.Cookie6Sided.toShape()), contentAlignment = Alignment.Center) { Icon(f.icon, null, Modifier.size(22.dp), tint = fg) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, style = MaterialTheme.typography.titleMedium)
            Text(f.body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (f.go != null) { Spacer(Modifier.width(12.dp)); Icon(Sym.ArrowForward, "Abrir ${f.title}", tint = fg) }
    }
}

/** A MaterialShape turning slowly in place; holds still when animations are off. */
@Composable
private fun Turning(shape: RoundedPolygon, color: Color, size: Dp, modifier: Modifier = Modifier, periodMs: Int = 20_000) {
    val still = LocalS.current.calm
    val r by rememberInfiniteTransition(label = "turn").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "r")
    Box(modifier.size(size).graphicsLayer { rotationZ = r }.background(color, shape.toShape()))
}
