// dashboard permite escolher grupos visiveis e monta graficos a partir dos dados da pagina
// preparar dashboard restaura grupos escolhidos e configura os controles e graficos
document.addEventListener('DOMContentLoaded', () => {
    const pagina = document.querySelector('.dashboard-page');
    // iniciar dashboard encerra quando a pagina do painel nao existe
    if (!pagina) return;
    const cores = ['#2864ad', '#18816d', '#b45b19', '#7655a4', '#aa4056', '#4d6b80'];
    const secoes = [...pagina.querySelectorAll('details[data-secao]')];
    const chave = `campuslink:dashboard:${pagina.dataset.perfil}:secoes`;
    const graficos = new Map();
    const vazios = new Set();
    let preferencias = {};
    // recuperar preferencias do dashboard tenta ler as secoes salvas no navegador
    try {
        const salvas = JSON.parse(localStorage.getItem(chave));
        // validar preferencias do dashboard aceita apenas um objeto de preferencias
        if (salvas && typeof salvas === 'object' && !Array.isArray(salvas)) preferencias = salvas;
    } catch (erro) {

    }

    // restaurar grupo do dashboard aplica a preferencia salva e acompanha mudancas
    secoes.forEach(secao => {
        // restaurar secao do dashboard aplica a preferencia quando ela indica abrir ou fechar
        if (typeof preferencias[secao.dataset.secao] === 'boolean') secao.open = preferencias[secao.dataset.secao];
        // salvar escolha do grupo guarda a abertura da secao e atualiza os graficos visiveis
        secao.addEventListener('toggle', () => {
            preferencias[secao.dataset.secao] = secao.open;
            // salvar preferencias do dashboard tenta guardar as secoes escolhidas no navegador
            try { localStorage.setItem(chave, JSON.stringify(preferencias)); } catch (erro) {  }
            // atualizar graficos abertos agenda o ajuste do grafico quando a secao e expandida
            if (secao.open) requestAnimationFrame(atualizarGraficos);
        });
    });

    // configurar acao do dashboard associa cada botao ao controle dos grupos
    pagina.querySelectorAll('[data-dashboard-action]').forEach(botao => {
        // aplicar visualizacao do dashboard atualiza os grupos conforme a acao escolhida
        botao.addEventListener('click', () => {
            const acao = botao.dataset.dashboardAction;
            // definir abertura dos grupos abre todos ou somente o resumo conforme a acao
            secoes.forEach(secao => {
                secao.open = acao === 'abrir' || (acao === 'padrao' && secao.dataset.secao === 'resumo');
            });
            requestAnimationFrame(atualizarGraficos);
        });
    });
    const acoes = pagina.querySelector('.dashboard-view-actions');
    // mostrar acoes do dashboard exibe os controles de abrir e fechar secoes
    if (acoes) acoes.hidden = false;
    requestAnimationFrame(atualizarGraficos);

    // atualizar graficos visiveis percorre os paineis abertos do dashboard
    function atualizarGraficos() {
        // ajustar painel de grafico redimensiona o grafico existente ou cria o que falta
        pagina.querySelectorAll('.dashboard-chart[open]').forEach(painel => {
            // selecionar graficos visiveis ignora rankings e secoes recolhidas
            if (painel.dataset.tipo === 'ranking' || painel.closest('details:not([open])')) return;
            // ajustar grafico existente redimensiona o grafico que ja foi criado
            if (graficos.has(painel)) {
                graficos.get(painel).resize();
            }
            // criar grafico pendente monta o grafico quando o painel ainda nao foi marcado como vazio
            else if (!vazios.has(painel)) {
                criarGrafico(painel);
            }
        });
    }

    // criar grafico do dashboard le os valores da tabela e mostra o grafico ou o aviso de vazio
    function criarGrafico(painel) {
        const linhas = [...painel.querySelectorAll('.dashboard-chart-data tbody tr')];
        // ler valor do grafico converte o conteudo da celula em numero
        const valores = linhas.map(linha => Number(linha.querySelector('td').textContent));
        // criar grafico com dados encerra quando a tabela nao possui linhas
        if (!linhas.length) return;
        // verificar dados positivos identifica se existe algum valor maior que zero
        // mostrar grafico vazio avisa quando nenhum valor do recorte e maior que zero
        if (!valores.some(valor => valor > 0)) {
            const aviso = document.createElement('p');
            aviso.className = 'dashboard-empty';
            aviso.textContent = 'Nenhum registro neste recorte.';
            painel.querySelector('.dashboard-canvas').after(aviso);
            vazios.add(painel);
            return;
        }
        // verificar recurso de graficos encerra quando a biblioteca de graficos nao esta disponivel
        if (typeof Chart === 'undefined') return;
        const tipo = painel.dataset.tipo;
        const container = painel.querySelector('.dashboard-canvas');
        container.hidden = false;
        // desenhar grafico do painel tenta montar o grafico e oculta sua area em caso de falha
        try {
            const grafico = new Chart(container.querySelector('canvas'), {
                type: tipo,
                data: {
                    // ler rotulo do grafico usa o titulo da linha como nome do dado
                    labels: linhas.map(linha => linha.querySelector('th').textContent),
                    datasets: [{
                        label: 'Quantidade', data: valores,
                        backgroundColor: tipo === 'line' ? '#2864ad' : cores,
                        borderColor: tipo === 'line' ? '#2864ad' : '#f5f7fb',
                        borderWidth: 2, tension: 0.2
                    }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    animation: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? false : { duration: 400 },
                    plugins: { legend: { display: tipo === 'doughnut', position: 'bottom' } },
                    scales: tipo === 'doughnut' ? {} : { y: { beginAtZero: true, ticks: { precision: 0 } } }
                }
            });
            graficos.set(painel, grafico);
            painel.querySelector('.dashboard-chart-values').open = false;
        } catch (erro) {
            container.hidden = true;
        }
    }
});
