// troca de tema alterna entre claro e escuro e guarda a escolha no navegador
(function () {
    const chave = 'campuslink:tema';
    const raiz = document.documentElement;

    // aplicar tema marca o html para o css escuro e para os componentes do bootstrap
    function aplicar(tema) {
        const escuro = tema === 'escuro';
        if (escuro) {
            raiz.dataset.tema = 'escuro';
            raiz.setAttribute('data-bs-theme', 'dark');
        } else {
            delete raiz.dataset.tema;
            raiz.removeAttribute('data-bs-theme');
        }
        // ajustar graficos troca a cor dos textos e das linhas de grade do chart js e redesenha os existentes
        if (window.Chart) {
            window.Chart.defaults.color = escuro ? '#c8cfe0' : '#666666';
            window.Chart.defaults.borderColor = escuro ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.1)';
            Object.values(window.Chart.instances || {}).forEach(grafico => grafico.update());
        }
        document.querySelectorAll('[data-tema-alternar]').forEach(botao => {
            botao.setAttribute('aria-pressed', String(escuro));
            botao.setAttribute('aria-label', escuro ? 'Ativar modo claro' : 'Ativar modo escuro');
            botao.title = escuro ? 'Modo claro' : 'Modo escuro';
        });
    }

    // ler escolha salva tenta recuperar o tema mesmo quando o armazenamento esta bloqueado
    function lerEscolha() {
        try {
            return localStorage.getItem(chave);
        } catch (erro) {
            return null;
        }
    }

    // salvar escolha guarda o tema sem quebrar a pagina quando o armazenamento esta bloqueado
    function salvarEscolha(tema) {
        try {
            localStorage.setItem(chave, tema);
        } catch (erro) {
            // ignorar armazenamento bloqueado mantem a troca valida apenas nesta visita
        }
    }

    aplicar(lerEscolha());

    // ligar botoes de tema alterna e salva a escolha a cada clique
    document.addEventListener('click', evento => {
        const botao = evento.target.closest('[data-tema-alternar]');
        // ignorar cliques fora do botao de tema segue o comportamento normal da pagina
        if (!botao) return;
        const novo = raiz.dataset.tema === 'escuro' ? 'claro' : 'escuro';
        salvarEscolha(novo);
        aplicar(novo);
    });

    // atualizar botoes depois do carregamento corrige o texto acessivel dos botoes criados depois do script
    document.addEventListener('DOMContentLoaded', () => aplicar(lerEscolha()));
})();
