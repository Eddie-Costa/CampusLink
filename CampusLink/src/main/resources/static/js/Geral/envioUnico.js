// envio de formularios bloqueia repeticoes e mostra o andamento conforme a acao
// preparar envio unico configura o bloqueio de formularios e a restauracao ao voltar
(function () {

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


    const SEM_TRAVA = [/\/exportar/i];

    // escolher texto de envio usa a mensagem do formulario ou uma mensagem conforme a acao
    function textoPara(form) {
        // escolher texto de envio usa o texto definido no formulario quando existe
        if (form.dataset.textoEnvio) return form.dataset.textoEnvio;
        const caminho = new URL(form.action, window.location.href).pathname;
        for (const [padrao, texto] of TEXTOS) {
            // identificar acao do formulario usa o texto correspondente ao caminho de envio
            if (padrao.test(caminho)) return texto;
        }
        return 'Enviando...';
    }

    // bloquear envio repetido aceita o primeiro envio e impede novos envios do mesmo formulario
    document.addEventListener('submit', function (evento) {
        const form = evento.target;
        // controlar envio unico ignora elementos que nao sao formularios
        if (!(form instanceof HTMLFormElement)) return;
        // limitar bloqueio de envio aplica o controle apenas a formularios enviados por post
        if ((form.method || '').toLowerCase() !== 'post') return;
        // respeitar envio cancelado encerra quando outra validacao ja impediu o envio
        if (evento.defaultPrevented) return;

        const caminho = new URL(form.action, window.location.href).pathname;
        // conferir formulario sem trava verifica se a acao baixa arquivo e dispensa o bloqueio
        // preservar formularios sem trava ignora os caminhos que dispensam bloqueio de envio
        if (SEM_TRAVA.some(function (p) { return p.test(caminho); })) return;

        // impedir envio repetido cancela o envio quando o formulario ja esta sendo enviado
        if (form.dataset.enviando === 'true') {
            evento.preventDefault();
            return;
        }
        form.dataset.enviando = 'true';

        const texto = textoPara(form);


        // agendar bloqueio dos botoes desativa os controles depois da preparacao do envio
        setTimeout(function () {
            // desativar botao enviado guarda o texto original e mostra a acao em andamento
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (botao) {
                botao.disabled = true;
                // mostrar envio no botao guarda o texto original e apresenta a acao em andamento
                if (botao.tagName === 'BUTTON') {
                    botao.dataset.textoOriginal = botao.textContent;
                    botao.textContent = texto;
                }
            });
        }, 0);
    });


    // restaurar pagina anterior libera os formularios quando a pagina volta da memoria do navegador
    window.addEventListener('pageshow', function (evento) {
        // restaurar pagina em cache ignora paginas que nao foram recuperadas do historico
        if (!evento.persisted) return;
        // liberar formulario anterior remove a marca de envio e restaura seus botoes
        document.querySelectorAll('form[data-enviando]').forEach(function (form) {
            delete form.dataset.enviando;
            // restaurar botao de envio habilita o controle e recupera o texto original
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (botao) {
                botao.disabled = false;
                // recuperar texto do botao restaura o texto guardado antes do envio
                if (botao.dataset.textoOriginal) botao.textContent = botao.dataset.textoOriginal;
            });
        });
    });
})();