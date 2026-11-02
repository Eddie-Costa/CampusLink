(function () {
    // Primeiro padrão que combinar com o endereço do formulário define o texto do botão.
    const TEXTOS = [
        [/\/excluir|\/remover|\/removerPessoas/i,           'Excluindo...'],
        [/\/denunciar/i,                                    'Enviando denúncia...'],
        [/\/solicitar-revisao/i,                            'Enviando...'],
        [/\/aprovar|\/liberar/i,                            'Aprovando...'],
        [/\/rejeitar|\/reprovar/i,                          'Rejeitando...'],
        [/\/desativar/i,                                    'Desativando...'],
        [/\/ativar/i,                                       'Ativando...'],
        [/\/editar/i,                                       'Salvando alterações...'],
        [/\/dominios/i,                                     'Enviando solicitação...'],
        [/\/adicionar|adicionarPessoas/i,                   'Adicionando...'],
        [/\/cadastrar|\/criarTurma|\/conteudos$/i,          'Cadastrando...'],
        [/\/login|\/verificar/i,                            'Entrando...'],
        [/\/reset/i,                                        'Enviando...']
    ];

    // Formulários que baixam arquivo (a página não recarrega) não devem ser travados.
    const SEM_TRAVA = [/\/exportar/i];

    function textoPara(form) {
        if (form.dataset.textoEnvio) return form.dataset.textoEnvio;
        const caminho = new URL(form.action, window.location.href).pathname;
        for (const [padrao, texto] of TEXTOS) {
            if (padrao.test(caminho)) return texto;
        }
        return 'Enviando...';
    }

    document.addEventListener('submit', function (evento) {
        const form = evento.target;
        if (!(form instanceof HTMLFormElement)) return;
        if ((form.method || '').toLowerCase() !== 'post') return;
        if (evento.defaultPrevented) return;      // cancelado por validação ou confirm()

        const caminho = new URL(form.action, window.location.href).pathname;
        if (SEM_TRAVA.some(function (p) { return p.test(caminho); })) return;

        if (form.dataset.enviando === 'true') {   // 2º clique: bloqueia
            evento.preventDefault();
            return;
        }
        form.dataset.enviando = 'true';

        const texto = textoPara(form);

        // desabilita depois que o navegador já montou os dados do formulário
        setTimeout(function () {
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (botao) {
                botao.disabled = true;
                if (botao.tagName === 'BUTTON') {
                    botao.dataset.textoOriginal = botao.textContent;
                    botao.textContent = texto;
                }
            });
        }, 0);
    });

    // voltar pelo botão "Voltar" do navegador não pode deixar o botão travado
    window.addEventListener('pageshow', function (evento) {
        if (!evento.persisted) return;
        document.querySelectorAll('form[data-enviando]').forEach(function (form) {
            delete form.dataset.enviando;
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (botao) {
                botao.disabled = false;
                if (botao.dataset.textoOriginal) botao.textContent = botao.dataset.textoOriginal;
            });
        });
    });
})();