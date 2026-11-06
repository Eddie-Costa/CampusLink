// conversa do chamado envia respostas e acompanha mensagens e encerramento do atendimento
// preparar conversa do chamado configura mensagens envio e atualizacoes periodicas
(function () {
    const pagina = document.getElementById('chamado-chat');
    // iniciar conversa do chamado encerra quando a pagina do chat nao existe
    if (!pagina) return;
    const lista = document.getElementById('chat-mensagens');
    const formulario = document.getElementById('chat-formulario');
    const campo = document.getElementById('resposta');
    const botao = document.getElementById('chat-enviar');
    const aviso = document.getElementById('chat-aviso');
    const sincronizacao = document.getElementById('chat-sincronizacao');
    const tipoProprio = pagina.dataset.admin === 'true' ? 'admin' : 'usuario';
    // identificar mensagem exibida extrai o codigo para evitar mensagens repetidas
    const exibidas = new Set(Array.from(lista.querySelectorAll('[data-mensagem-id]'), item => item.dataset.mensagemId));
    let enviando = false;
    let atualizando = false;
    let encerrado = !document.getElementById('chat-encerrado').hidden;
    let sessaoExpirada = false;
    let versao = 0;

    // mostrar aviso da conversa exibe o texto recebido ou oculta a area vazia
    function mostrarAviso(texto) {
        aviso.textContent = texto;
        aviso.hidden = !texto;
    }

    // descrever erro da conversa distingue falha de conexao das demais mensagens de erro
    function mensagemErro(erro) {
        return erro instanceof TypeError
            ? 'Não foi possível conectar. Sua mensagem foi mantida; aguarde a conversa atualizar antes de reenviar.'
            : erro.message;
    }

    // formatar data da conversa mostra data e hora no fuso de sao paulo
    function formatarData(valor) {
        return new Date(valor).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo', dateStyle: 'short', timeStyle: 'short' });
    }

    // adicionar mensagem do chamado mostra autor texto e horario sem repetir mensagens existentes
    function adicionarMensagem(item) {
        // evitar mensagem repetida ignora mensagens que ja foram exibidas
        if (exibidas.has(String(item.id))) return;
        const artigo = document.createElement('article');
        artigo.className = 'chat-mensagem ' + (item.tipoAutor === tipoProprio ? 'chat-enviada' : 'chat-recebida');
        artigo.dataset.mensagemId = item.id;
        const balao = document.createElement('div');
        balao.className = 'chat-balao';
        const autor = document.createElement('p');
        autor.className = 'chat-autor';
        autor.textContent = item.nomeAutor;
        // identificar equipe no chat acrescenta a marca de equipe nas mensagens administrativas
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

    // atualizar conversa aplica respostas situacao encerramento acoes e rolagem
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
        // mostrar conclusao do chamado apresenta a data quando o atendimento foi concluido
        if (chamado.concluidoEm) {
            document.getElementById('chat-conclusao-rotulo').hidden = false;
            const conclusao = document.getElementById('chat-conclusao-data');
            conclusao.hidden = false;
            conclusao.textContent = formatarData(chamado.concluidoEm);
        }
        const acoesAdmin = document.getElementById('chat-acoes-admin');
        // atualizar acoes administrativas controla a conclusao e a orientacao de resposta no chat
        if (acoesAdmin) {
            acoesAdmin.hidden = encerrado;
            document.getElementById('chat-concluir').disabled = !chamado.possuiRespostaAdmin || encerrado;
            document.getElementById('chat-orientacao-conclusao').hidden = chamado.possuiRespostaAdmin;
        }
        sincronizacao.textContent = encerrado ? 'Atendimento concluído · Histórico disponível' : 'Conversa atualizada automaticamente';
        // acompanhar fim da conversa rola para a ultima mensagem quando solicitado ou proximo do final
        if (pertoDoFim || rolar) lista.scrollTop = lista.scrollHeight;
    }

    // ler resposta da conversa identifica sessao encerrada e falhas antes de retornar os dados
    async function lerResposta(resposta) {
        // verificar sessao do chat interrompe a conversa quando o acesso expira ou e recusado
        if (resposta.status === 401 || resposta.status === 403 || resposta.redirected) {
            sessaoExpirada = true;
            throw new Error('Sua sessão expirou ou o acesso foi encerrado. Entre novamente para continuar.');
        }
        // tratar falha do chat prepara a mensagem de erro quando a resposta nao tem sucesso
        if (!resposta.ok) {
            let dados = {};
            // ler erro do chat interpreta o corpo apenas quando a resposta informa formato json
            if ((resposta.headers.get('content-type') || '').includes('json')) dados = await resposta.json();
            throw new Error(dados.mensagem || 'Não foi possível atualizar a conversa. Aguarde a reconexão antes de reenviar.');
        }
        return resposta.json();
    }

    // buscar atualizacao do chamado consulta a conversa visivel e descarta respostas antigas
    async function atualizar() {
        // controlar atualizacao do chat evita consultas simultaneas ou sem uma conversa ativa e visivel
        if (enviando || atualizando || encerrado || sessaoExpirada || document.hidden) return;
        atualizando = true;
        const versaoPedido = ++versao;
        // consultar conversa tenta atualizar as mensagens e informar falhas de conexao
        try {
            const resposta = await fetch(pagina.dataset.conversaUrl, { headers: { Accept: 'application/json' }, cache: 'no-store' });
            const chamado = await lerResposta(resposta);
            // descartar conversa antiga ignora respostas de uma versao anterior da conversa
            if (versaoPedido !== versao) return;
            atualizarConversa(chamado, false);
            mostrarAviso('');
        } catch (erro) {
            // mostrar falha atual do chat avisa sobre a conexao somente para a versao atual
            if (versaoPedido === versao) {
                sincronizacao.textContent = 'Conexão interrompida';
                mostrarAviso(mensagemErro(erro));
            }
        } finally {
            atualizando = false;
        }
    }

    // enviar mensagem do chamado valida o texto bloqueia repeticoes e atualiza a conversa
    formulario.addEventListener('submit', async function (evento) {
        evento.preventDefault();
        // controlar envio do chat impede envios simultaneos ou em conversa encerrada ou sessao expirada
        if (enviando || encerrado || sessaoExpirada) return;
        // validar mensagem do chat exige texto antes de enviar
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
        // enviar mensagem do chamado tenta salvar a mensagem e atualizar a conversa
        try {
            const resposta = await fetch(formulario.dataset.envioUrl, { method: 'POST', body: dados, headers: { Accept: 'application/json' } });
            const chamado = await lerResposta(resposta);
            // limpar mensagem enviada apaga o campo apenas se o texto nao mudou durante o envio
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

    // enviar conversa pelo teclado usa control ou comando com enter para enviar o formulario
    campo.addEventListener('keydown', function (evento) {
        // enviar mensagem pelo teclado usa control ou comando com enter para enviar o formulario
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
