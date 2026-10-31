(function () {
    const pagina = document.getElementById('chamado-chat');
    if (!pagina) return;
    const lista = document.getElementById('chat-mensagens');
    const formulario = document.getElementById('chat-formulario');
    const campo = document.getElementById('resposta');
    const botao = document.getElementById('chat-enviar');
    const aviso = document.getElementById('chat-aviso');
    const sincronizacao = document.getElementById('chat-sincronizacao');
    const tipoProprio = pagina.dataset.admin === 'true' ? 'admin' : 'usuario';
    const exibidas = new Set(Array.from(lista.querySelectorAll('[data-mensagem-id]'), item => item.dataset.mensagemId));
    let enviando = false;
    let atualizando = false;
    let encerrado = !document.getElementById('chat-encerrado').hidden;
    let sessaoExpirada = false;
    let versao = 0;

    function mostrarAviso(texto) {
        aviso.textContent = texto;
        aviso.hidden = !texto;
    }

    function mensagemErro(erro) {
        return erro instanceof TypeError
            ? 'Não foi possível conectar. Sua mensagem foi mantida; aguarde a conversa atualizar antes de reenviar.'
            : erro.message;
    }

    function formatarData(valor) {
        return new Date(valor).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo', dateStyle: 'short', timeStyle: 'short' });
    }

    function adicionarMensagem(item) {
        if (exibidas.has(String(item.id))) return;
        const artigo = document.createElement('article');
        artigo.className = 'chat-mensagem ' + (item.tipoAutor === tipoProprio ? 'chat-enviada' : 'chat-recebida');
        artigo.dataset.mensagemId = item.id;
        const balao = document.createElement('div');
        balao.className = 'chat-balao';
        const autor = document.createElement('p');
        autor.className = 'chat-autor';
        autor.textContent = item.nomeAutor;
        if (item.tipoAutor === 'admin') {
            const equipe = document.createElement('span');
            equipe.className = 'chat-equipe';
            equipe.textContent = 'Equipe';
            autor.appendChild(equipe);
        }
        const texto = document.createElement('p');
        texto.className = 'chat-texto';
        texto.textContent = item.resposta;
        const horario = document.createElement('time');
        horario.className = 'chat-hora';
        horario.dateTime = item.createdAt;
        horario.textContent = formatarData(item.createdAt);
        balao.append(autor, texto, horario);
        artigo.appendChild(balao);
        lista.appendChild(artigo);
        exibidas.add(String(item.id));
    }

    function atualizarConversa(chamado, rolar) {
        const pertoDoFim = lista.scrollHeight - lista.scrollTop - lista.clientHeight < 100;
        chamado.respostas.forEach(adicionarMensagem);
        encerrado = chamado.status === 'concluido';
        const situacao = document.getElementById('chat-status');
        situacao.className = 'chamados-status ' + chamado.status;
        situacao.textContent = chamado.statusDescricao;
        document.getElementById('chat-encerrado').hidden = !encerrado;
        formulario.hidden = encerrado;
        campo.disabled = encerrado;
        botao.disabled = encerrado || enviando;
        if (chamado.concluidoEm) {
            document.getElementById('chat-conclusao-rotulo').hidden = false;
            const conclusao = document.getElementById('chat-conclusao-data');
            conclusao.hidden = false;
            conclusao.textContent = formatarData(chamado.concluidoEm);
        }
        const acoesAdmin = document.getElementById('chat-acoes-admin');
        if (acoesAdmin) {
            acoesAdmin.hidden = encerrado;
            document.getElementById('chat-concluir').disabled = !chamado.possuiRespostaAdmin || encerrado;
            document.getElementById('chat-orientacao-conclusao').hidden = chamado.possuiRespostaAdmin;
        }
        sincronizacao.textContent = encerrado ? 'Atendimento concluído · Histórico disponível' : 'Conversa atualizada automaticamente';
        if (pertoDoFim || rolar) lista.scrollTop = lista.scrollHeight;
    }

    async function lerResposta(resposta) {
        if (resposta.status === 401 || resposta.status === 403 || resposta.redirected) {
            sessaoExpirada = true;
            throw new Error('Sua sessão expirou ou o acesso foi encerrado. Entre novamente para continuar.');
        }
        if (!resposta.ok) {
            let dados = {};
            if ((resposta.headers.get('content-type') || '').includes('json')) dados = await resposta.json();
            throw new Error(dados.mensagem || 'Não foi possível atualizar a conversa. Aguarde a reconexão antes de reenviar.');
        }
        return resposta.json();
    }

    async function atualizar() {
        if (enviando || atualizando || encerrado || sessaoExpirada || document.hidden) return;
        atualizando = true;
        const versaoPedido = ++versao;
        try {
            const resposta = await fetch(pagina.dataset.conversaUrl, { headers: { Accept: 'application/json' }, cache: 'no-store' });
            const chamado = await lerResposta(resposta);
            if (versaoPedido !== versao) return;
            atualizarConversa(chamado, false);
            mostrarAviso('');
        } catch (erro) {
            if (versaoPedido === versao) {
                sincronizacao.textContent = 'Conexão interrompida';
                mostrarAviso(mensagemErro(erro));
            }
        } finally {
            atualizando = false;
        }
    }

    formulario.addEventListener('submit', async function (evento) {
        evento.preventDefault();
        if (enviando || encerrado || sessaoExpirada) return;
        if (!campo.value.trim()) {
            mostrarAviso('Escreva uma mensagem antes de enviar.');
            campo.focus();
            return;
        }
        const dados = new FormData(formulario);
        const textoEnviado = campo.value;
        enviando = true;
        ++versao;
        botao.disabled = true;
        botao.textContent = 'Enviando...';
        mostrarAviso('');
        try {
            const resposta = await fetch(formulario.dataset.envioUrl, { method: 'POST', body: dados, headers: { Accept: 'application/json' } });
            const chamado = await lerResposta(resposta);
            if (campo.value === textoEnviado) campo.value = '';
            atualizarConversa(chamado, true);
            campo.focus();
        } catch (erro) {
            mostrarAviso(mensagemErro(erro));
        } finally {
            enviando = false;
            botao.disabled = encerrado || sessaoExpirada;
            botao.textContent = 'Enviar ↗';
        }
    });

    campo.addEventListener('keydown', function (evento) {
        if ((evento.ctrlKey || evento.metaKey) && evento.key === 'Enter') {
            evento.preventDefault();
            formulario.requestSubmit();
        }
    });
    lista.scrollTop = lista.scrollHeight;
    atualizar();
    window.setInterval(atualizar, 5000);
    document.addEventListener('visibilitychange', atualizar);
})();
