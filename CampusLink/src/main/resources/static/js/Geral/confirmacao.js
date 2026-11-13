// confirmacao padronizada abre um modal antes de acoes como excluir remover ou desativar
(function () {
    let dialogo = null;
    let resolverAtual = null;

    // montar dialogo cria uma unica janela de confirmacao reutilizada pela pagina inteira
    function obterDialogo() {
        // reaproveitar dialogo existente evita criar varias janelas iguais
        if (dialogo && document.body.contains(dialogo)) return dialogo;
        dialogo = document.createElement('dialog');
        dialogo.className = 'confirmacao';
        dialogo.setAttribute('aria-labelledby', 'confirmacaoTitulo');
        dialogo.setAttribute('aria-describedby', 'confirmacaoMensagem');
        dialogo.innerHTML = `
            <div class="confirmacao-icone" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 9v4"/><path d="M12 17v.01"/><path d="M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/></svg>
            </div>
            <h2 id="confirmacaoTitulo" class="confirmacao-titulo"></h2>
            <p class="confirmacao-item" hidden></p>
            <p id="confirmacaoMensagem" class="confirmacao-mensagem"></p>
            <div class="confirmacao-acoes">
                <button type="button" class="confirmacao-cancelar">Cancelar</button>
                <button type="button" class="confirmacao-confirmar"></button>
            </div>`;
        dialogo.querySelector('.confirmacao-cancelar').addEventListener('click', () => fechar(false));
        dialogo.querySelector('.confirmacao-confirmar').addEventListener('click', () => fechar(true));
        // tratar tecla esc fecha a janela como cancelamento
        dialogo.addEventListener('cancel', evento => { evento.preventDefault(); fechar(false); });
        // fechar ao clicar fora cancela quando o clique acontece no fundo escurecido
        dialogo.addEventListener('click', evento => { if (evento.target === dialogo) fechar(false); });
        document.body.appendChild(dialogo);
        return dialogo;
    }

    // fechar dialogo devolve a resposta para quem pediu a confirmacao
    function fechar(resposta) {
        // ignorar fechamento sem pedido evita resolver duas vezes a mesma confirmacao
        if (!dialogo || !resolverAtual) return;
        dialogo.close();
        const resolver = resolverAtual;
        resolverAtual = null;
        resolver(resposta);
    }

    // confirmar acao abre o modal com titulo item mensagem e texto do botao e devolve uma promessa
    function confirmar({ titulo = 'Confirmar ação', mensagem = '', item = '', botao = 'Confirmar', perigo = true } = {}) {
        const janela = obterDialogo();
        // cancelar pedido anterior responde nao para uma confirmacao que ficou aberta
        if (resolverAtual) fechar(false);
        janela.classList.toggle('confirmacao-perigo', perigo);
        janela.querySelector('.confirmacao-titulo').textContent = titulo;
        const campoItem = janela.querySelector('.confirmacao-item');
        campoItem.textContent = item;
        campoItem.hidden = !item;
        janela.querySelector('.confirmacao-mensagem').textContent = mensagem;
        janela.querySelector('.confirmacao-confirmar').textContent = botao;
        return new Promise(resolver => {
            resolverAtual = resolver;
            janela.showModal();
            janela.querySelector('.confirmacao-cancelar').focus();
        });
    }

    // ler pedido de confirmacao junta os atributos do botao clicado e do formulario
    function lerPedido(form, botao) {
        const ler = nome => (botao && botao.dataset[nome]) || form.dataset[nome] || '';
        const mensagem = ler('confirmar');
        const tipo = ler('confirmarTipo');
        const titulo = ler('confirmarTitulo') || 'Confirmar ação';
        const textoBotao = ler('confirmarBotao') || (botao ? botao.textContent.replace(/\s+/g, ' ').trim() : '') || 'Confirmar';
        return {
            mensagem,
            titulo,
            item: ler('confirmarItem'),
            botao: textoBotao,
            // identificar acao perigosa usa o tipo informado ou palavras de exclusao no titulo botao ou mensagem
            perigo: tipo ? tipo === 'perigo' : /exclu|remov|desativ|revog|reprov|rejeit|apag/i.test(`${titulo} ${textoBotao} ${mensagem}`)
        };
    }

    // interceptar envio pede confirmacao antes de enviar formularios marcados com data-confirmar
    document.addEventListener('submit', evento => {
        const form = evento.target;
        const botao = evento.submitter || null;
        // ignorar formularios sem confirmacao segue o envio normal
        if (!(form instanceof HTMLFormElement) || !((botao && botao.dataset.confirmar) || form.dataset.confirmar)) return;
        // liberar envio confirmado deixa passar o envio repetido depois do sim
        if (form.dataset.confirmado === 'sim') {
            delete form.dataset.confirmado;
            return;
        }
        evento.preventDefault();
        evento.stopImmediatePropagation();
        confirmar(lerPedido(form, botao)).then(confirmado => {
            // reenviar formulario confirmado repete o envio com o mesmo botao clicado
            if (!confirmado) return;
            form.dataset.confirmado = 'sim';
            if (typeof form.requestSubmit === 'function') form.requestSubmit(botao && botao.form === form ? botao : undefined);
            else form.submit();
        });
    }, true);

    window.campusConfirmar = confirmar;
})();
