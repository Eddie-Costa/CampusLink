document.addEventListener('DOMContentLoaded', () => {
    const pagina = document.querySelector('.dashboard-page');
    if (!pagina) return;
    const cores = ['#2864ad', '#18816d', '#b45b19', '#7655a4', '#aa4056', '#4d6b80'];
    const secoes = [...pagina.querySelectorAll('details[data-secao]')];
    const chave = `campuslink:dashboard:${pagina.dataset.perfil}:secoes`;
    const graficos = new Map();
    const vazios = new Set();
    let preferencias = {};
    try {
        const salvas = JSON.parse(localStorage.getItem(chave));
        if (salvas && typeof salvas === 'object' && !Array.isArray(salvas)) preferencias = salvas;
    } catch (erro) {
        // os grupos continuam funcionando quando o armazenamento não está disponível
    }

    secoes.forEach(secao => {
        if (typeof preferencias[secao.dataset.secao] === 'boolean') secao.open = preferencias[secao.dataset.secao];
        secao.addEventListener('toggle', () => {
            preferencias[secao.dataset.secao] = secao.open;
            try { localStorage.setItem(chave, JSON.stringify(preferencias)); } catch (erro) { /* mantém a escolha nesta página */ }
            if (secao.open) requestAnimationFrame(atualizarGraficos);
        });
    });

    pagina.querySelectorAll('[data-dashboard-action]').forEach(botao => {
        botao.addEventListener('click', () => {
            const acao = botao.dataset.dashboardAction;
            secoes.forEach(secao => {
                secao.open = acao === 'abrir' || (acao === 'padrao' && secao.dataset.secao === 'resumo');
            });
            requestAnimationFrame(atualizarGraficos);
        });
    });
    const acoes = pagina.querySelector('.dashboard-view-actions');
    if (acoes) acoes.hidden = false;
    requestAnimationFrame(atualizarGraficos);

    function atualizarGraficos() {
        pagina.querySelectorAll('.dashboard-chart[open]').forEach(painel => {
            if (painel.dataset.tipo === 'ranking' || painel.closest('details:not([open])')) return;
            if (graficos.has(painel)) {
                graficos.get(painel).resize();
            } else if (!vazios.has(painel)) {
                criarGrafico(painel);
            }
        });
    }

    function criarGrafico(painel) {
        const linhas = [...painel.querySelectorAll('.dashboard-chart-data tbody tr')];
        const valores = linhas.map(linha => Number(linha.querySelector('td').textContent));
        if (!linhas.length) return;
        if (!valores.some(valor => valor > 0)) {
            const aviso = document.createElement('p');
            aviso.className = 'dashboard-empty';
            aviso.textContent = 'Nenhum registro neste recorte.';
            painel.querySelector('.dashboard-canvas').after(aviso);
            vazios.add(painel);
            return;
        }
        if (typeof Chart === 'undefined') return;
        const tipo = painel.dataset.tipo;
        const container = painel.querySelector('.dashboard-canvas');
        container.hidden = false;
        try {
            const grafico = new Chart(container.querySelector('canvas'), {
                type: tipo,
                data: {
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
