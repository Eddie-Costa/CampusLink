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
    function lerEscolha(nome = chave) {
        try {
            return localStorage.getItem(nome);
        } catch (erro) {
            return null;
        }
    }

    // salvar escolha guarda o tema sem quebrar a pagina quando o armazenamento esta bloqueado
    function salvarEscolha(valor, nome = chave) {
        try {
            localStorage.setItem(nome, valor);
        } catch (erro) {
            // ignorar armazenamento bloqueado mantem a troca valida apenas nesta visita
        }
    }

    aplicar(lerEscolha());

    // reduzir movimento segue a escolha salva e quando nao ha escolha usa a preferencia do sistema
    const chaveMovimento = 'campuslink:movimento';
    const sistemaReduz = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // aplicar movimento marca o html para o css parar animacoes e atualiza o estado dos botoes
    function aplicarMovimento(escolha) {
        const reduzido = escolha ? escolha === 'reduzido' : sistemaReduz;
        raiz.dataset.movimento = reduzido ? 'reduzido' : 'normal';
        document.querySelectorAll('[data-movimento-alternar]').forEach(botao => {
            botao.setAttribute('aria-pressed', String(reduzido));
            botao.setAttribute('aria-label', reduzido ? 'Ativar movimento' : 'Reduzir movimento');
            botao.title = reduzido ? 'Ativar movimento' : 'Reduzir movimento';
        });
    }

    aplicarMovimento(lerEscolha(chaveMovimento));

    // ligar botoes de tema e de movimento alterna e salva a escolha a cada clique
    document.addEventListener('click', evento => {
        // trocar movimento inverte o estado atual e guarda a nova escolha
        if (evento.target.closest('[data-movimento-alternar]')) {
            const novoMovimento = raiz.dataset.movimento === 'reduzido' ? 'normal' : 'reduzido';
            salvarEscolha(novoMovimento, chaveMovimento);
            aplicarMovimento(novoMovimento);
            return;
        }
        const botao = evento.target.closest('[data-tema-alternar]');
        // ignorar cliques fora dos botoes segue o comportamento normal da pagina
        if (!botao) return;
        const novo = raiz.dataset.tema === 'escuro' ? 'claro' : 'escuro';
        salvarEscolha(novo);
        aplicar(novo);
    });

    // atualizar botoes depois do carregamento corrige o texto acessivel dos botoes criados depois do script
    document.addEventListener('DOMContentLoaded', () => {
        aplicar(lerEscolha());
        aplicarMovimento(lerEscolha(chaveMovimento));
        // criar fundo dinamico coloca as manchas animadas direto no body para ficarem atras de todo o conteudo
        let fundo = document.querySelector('.fundo-dinamico');
        if (!fundo) {
            fundo = document.createElement('div');
            fundo.className = 'fundo-dinamico';
            fundo.setAttribute('aria-hidden', 'true');
            fundo.innerHTML = '<span></span><span></span><span></span>';
            document.body.prepend(fundo);
        }

        // acompanhar rolagem informa ao css quanto da pagina ja foi percorrido para mover as luzes
        let agendado = false;
        const atualizarRolagem = () => {
            const total = document.documentElement.scrollHeight - window.innerHeight;
            fundo.style.setProperty('--rolagem', total > 0 ? (window.scrollY / total).toFixed(3) : '0');
            agendado = false;
        };
        window.addEventListener('scroll', () => {
            // limitar atualizacoes a uma por quadro evita trabalho extra durante a rolagem
            if (!agendado) {
                agendado = true;
                requestAnimationFrame(atualizarRolagem);
            }
        }, { passive: true });
        atualizarRolagem();
    });
})();
