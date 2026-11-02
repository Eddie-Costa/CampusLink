document.addEventListener('DOMContentLoaded', () => {
    const cores = ['#2864ad', '#18816d', '#b45b19', '#7655a4', '#aa4056', '#4d6b80'];
    document.querySelectorAll('.dashboard-chart').forEach(painel => {
        const linhas = [...painel.querySelectorAll('.dashboard-chart-data tbody tr')];
        const valores = linhas.map(linha => Number(linha.querySelector('td').textContent));
        if (!linhas.length) return;
        if (!valores.some(valor => valor > 0)) {
            const aviso = document.createElement('p');
            aviso.className = 'dashboard-empty';
            aviso.textContent = 'Nenhum registro neste recorte.';
            painel.querySelector('.dashboard-canvas').after(aviso);
            return;
        }
        if (typeof Chart === 'undefined') return;
        const tipo = painel.dataset.tipo;
        const container = painel.querySelector('.dashboard-canvas');
        container.hidden = false;
        try {
            new Chart(container.querySelector('canvas'), {
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
            painel.querySelector('details').open = false;
        } catch (erro) {
            container.hidden = true;
        }
    });
});
