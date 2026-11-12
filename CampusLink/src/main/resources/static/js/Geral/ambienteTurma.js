// ambiente da turma controla participantes conteudos buscas de livros e videos e formularios
// configurar busca automatica aguarda a pausa na digitacao antes de buscar resultados
function configurarBuscaAutomatica(input, buscar, resultados) {
    // configurar busca automatica exige o campo de busca e a area de resultados
    if (!input || !resultados) return;

    let temporizadorBusca;
    // agendar busca limpa os resultados e reinicia a espera quando o texto muda
    input.addEventListener('input', function () {
        window.clearTimeout(temporizadorBusca);
        resultados.replaceChildren();

        // enviar busca automatica ignora um termo vazio
        if (!input.value.trim()) {
            return;
        }

        temporizadorBusca = window.setTimeout(buscar, 500);
    });
}

// carregar livros da turma agrupa os livros exibidos e inicia a busca dos detalhes
document.addEventListener('DOMContentLoaded', function () {
    const itensLivro = Array.from(document.querySelectorAll('.conteudo-midia[data-book-id]'))
        // filtrar livros identificados seleciona os itens que possuem codigo de livro
        .filter(function (item) { return item.dataset.bookId; });
    const itensPorLivroId = new Map();

    // agrupar cartoes de livro reune os itens que mostram o mesmo livro
    itensLivro.forEach(function (item) {
        const itens = itensPorLivroId.get(item.dataset.bookId) || [];
        itens.push(item);
        itensPorLivroId.set(item.dataset.bookId, itens);
    });

    // buscar detalhes do livro consulta os dados e informa falhas da resposta
    async function carregarDetalhesLivro(volumeId) {
        const parametros = new URLSearchParams({ id: volumeId });
        const resposta = await fetch('/googlebooks/detalhes?' + parametros.toString(), {
            headers: { Accept: 'application/json' }
        });
        // carregar dados do livro informa falha quando o servidor recusa a consulta
        if (!resposta.ok) {
            let mensagem = 'Não foi possível carregar os dados do livro.';
            // ler erro do livro tenta aproveitar a mensagem devolvida pelo servidor
            try {
                const erro = await resposta.json();
                mensagem = erro.message || mensagem;
            } catch (erroLeitura) {

            }
            throw new Error(mensagem);
        }

        return resposta.json();
    }

    // carregar lote de livros busca varios livros e trata cada resultado separadamente
    async function carregarLoteLivros(volumeIds) {
        const resultados = await Promise.allSettled(volumeIds.map(carregarDetalhesLivro));

        // tratar resultado do livro atualiza os cartoes ou informa que os dados estao indisponiveis
        resultados.forEach(function (resultado, indice) {
            const volumeId = volumeIds[indice];
            const itens = itensPorLivroId.get(volumeId) || [];

            // carregar lote de livros mostra indisponibilidade nos itens cuja consulta falhou
            if (resultado.status === 'rejected') {
                console.warn('Não foi possível carregar os detalhes do livro.', resultado.reason);
                // mostrar livro indisponivel substitui o titulo e limpa o autor quando a consulta falha
                itens.forEach(function (item) {
                    const titulo = item.querySelector('.conteudo-midia-livro-titulo');
                    const autor = item.querySelector('.conteudo-midia-livro-autor');
                    // avisar livro indisponivel atualiza o titulo quando o elemento existe
                    if (titulo) titulo.textContent = 'Informações do livro indisponíveis.';
                    // limpar autor indisponivel apaga o texto do autor quando a consulta do livro falha
                    if (autor) autor.textContent = '';
                });
                return;
            }

            const livro = resultado.value;
            // preencher cartao de livro mostra titulo autores ano capa e link acessivel
            itens.forEach(function (item) {
                const titulo = item.querySelector('.conteudo-midia-livro-titulo');
                const autor = item.querySelector('.conteudo-midia-livro-autor');
                const imagem = item.querySelector('.conteudo-midia-link img');
                const link = item.querySelector('.conteudo-midia-link');

                // mostrar titulo do livro preenche o titulo e o texto de ajuda do elemento
                if (titulo) {
                    titulo.textContent = livro.titulo || 'Livro do Google Books';
                    titulo.title = livro.titulo || '';
                }
                // mostrar autores do livro junta os autores e o ano de publicacao disponiveis
                if (autor) {
                    autor.textContent = [livro.autores, livro.anoPublicacao]
                        .filter(Boolean)
                        .join(' · ');
                }
                // identificar link do livro acrescenta o titulo na descricao acessivel do link
                if (link && livro.titulo) {
                    link.setAttribute('aria-label', 'Abrir livro: ' + livro.titulo);
                }
                // mostrar capa do livro carrega a imagem e define uma alternativa caso ela falhe
                if (imagem && livro.thumbnailUrl) {
                    // recuperar capa do livro usa a imagem alternativa quando a capa nao carrega
                    imagem.onerror = function () {
                        imagem.onerror = null;
                        imagem.src = item.dataset.fallbackThumbnail;
                    };
                    imagem.src = livro.thumbnailUrl.replace(/^http:/i, 'https:');
                }
            });
        });
    }

    const idsLivros = Array.from(itensPorLivroId.keys());
    const tamanhoLoteLivros = 5;
    // carregar livros em grupos percorre os codigos em lotes de cinco
    async function carregarDetalhesLivros() {
        for (let inicio = 0; inicio < idsLivros.length; inicio += tamanhoLoteLivros) {
            await carregarLoteLivros(idsLivros.slice(inicio, inicio + tamanhoLoteLivros));
        }
    }

    carregarDetalhesLivros();
});

// preparar conteudos da turma configura os detalhes recolhiveis e carrega os videos em lotes
document.addEventListener('DOMContentLoaded', function () {
    // configurar botao de detalhes associa o clique ao controle do cartao
    document.querySelectorAll('.conteudo-alternar-detalhes').forEach(function (botao) {
        // alternar detalhes do conteudo abre ou fecha a area e atualiza a descricao do botao
        botao.addEventListener('click', function () {
            const detalhesId = botao.getAttribute('aria-controls');
            const detalhes = detalhesId ? document.getElementById(detalhesId) : null;
            // preencher detalhes da midia ignora itens sem area de detalhes
            if (!detalhes) return;

            const expandido = botao.getAttribute('aria-expanded') === 'true';
            const novoEstado = !expandido;
            detalhes.hidden = !novoEstado;
            botao.setAttribute('aria-expanded', String(novoEstado));

            const cartao = botao.closest('.conteudo-card');
            const nomeConteudo = cartao ? cartao.querySelector('.conteudo-nome') : null;
            const titulo = nomeConteudo ? nomeConteudo.textContent.trim() : '';
            const acao = novoEstado ? 'Recolher' : 'Expandir';
            const descricaoAcao = titulo ? `${acao} detalhes de ${titulo}` : `${acao} detalhes do conteúdo`;
            botao.setAttribute('aria-label', descricaoAcao);
            botao.title = descricaoAcao;
        });
    });

    const itensVideo = Array.from(document.querySelectorAll('.conteudo-midia[data-video-id]'))
        // filtrar videos identificados seleciona os itens que possuem codigo de video
        .filter(function (item) { return item.dataset.videoId; });
    const itensPorVideoId = new Map();

    // agrupar cartoes de video reune os itens que mostram o mesmo video
    itensVideo.forEach(function (item) {
        const itens = itensPorVideoId.get(item.dataset.videoId) || [];
        itens.push(item);
        itensPorVideoId.set(item.dataset.videoId, itens);
    });

    const idsVideos = Array.from(itensPorVideoId.keys());
    const tamanhoLote = 50;

    // buscar detalhes dos videos consulta um lote e distribui os dados entre os cartoes
    async function carregarDetalhesVideos(ids) {
        const parametros = new URLSearchParams({ ids: ids.join(',') });
        const resposta = await fetch('/youtube/detalhes?' + parametros.toString(), {
            headers: { Accept: 'application/json' }
        });
        // carregar detalhes dos videos informa falha quando o servidor recusa a consulta
        if (!resposta.ok) {
            throw new Error('Falha ao carregar os detalhes dos vídeos.');
        }

        const videos = await resposta.json();
        // indexar video associa o codigo do video aos dados recebidos
        const videosPorId = new Map(videos.map(function (video) {
            return [video.videoId, video];
        }));

        // distribuir dados do video localiza os cartoes que precisam receber os detalhes
        ids.forEach(function (videoId) {
            const video = videosPorId.get(videoId);
            const itens = itensPorVideoId.get(videoId) || [];

            // preencher cartao de video mostra titulo canal e duracao ou informa dados indisponiveis
            itens.forEach(function (item) {
                const titulo = item.querySelector('.conteudo-midia-titulo');
                const detalhes = item.querySelector('.conteudo-midia-detalhes');
                // preencher video exige elementos para o titulo e os detalhes
                if (!titulo || !detalhes) return;

                // mostrar video indisponivel avisa quando os dados do video nao foram encontrados
                if (!video) {
                    titulo.textContent = 'Informações do vídeo indisponíveis.';
                    return;
                }

                titulo.textContent = video.titulo || 'Vídeo do YouTube';
                titulo.title = video.titulo || '';
                detalhes.textContent = [video.canal, video.duracao].filter(Boolean).join(' · ');
            });
        });
    }

    for (let inicio = 0; inicio < idsVideos.length; inicio += tamanhoLote) {
        const lote = idsVideos.slice(inicio, inicio + tamanhoLote);
        // tratar falha dos videos registra o erro e marca os itens do lote como indisponiveis
        carregarDetalhesVideos(lote).catch(function (erro) {
            console.error('Não foi possível carregar os detalhes dos vídeos da turma.', erro);
            // localizar videos sem resposta percorre os cartoes do video que falhou
            lote.forEach(function (videoId) {
                // mostrar video indisponivel substitui o titulo quando os detalhes nao carregam
                (itensPorVideoId.get(videoId) || []).forEach(function (item) {
                    const titulo = item.querySelector('.conteudo-midia-titulo');
                    // avisar video indisponivel altera o titulo apenas quando o elemento existe
                    if (titulo) titulo.textContent = 'Informações do vídeo indisponíveis.';
                });
            });
        });
    }
});

// preparar interacoes da turma configura buscas participantes formularios e confirmacoes
document.addEventListener('DOMContentLoaded', function () {
    const buscaParticipantes = document.getElementById('buscaParticipantesTurma');

    // buscar participantes configura o filtro quando o campo de busca existe
    if (buscaParticipantes) {
        const linhasParticipantes = Array.from(
            document.querySelectorAll('#listaParticipantesTurma tbody tr')
        );
        const mensagemSemResultados = document.querySelector(
            '#participantesTurma .participantes-sem-resultados'
        );

        // normalizar busca de participante remove acentos e diferencia apenas o conteudo das letras
        function normalizarBuscaParticipante(texto) {
            return texto
                .normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '')
                .toLocaleLowerCase('pt-BR');
        }

        // buscar participantes filtra as linhas e informa quando nenhuma pessoa corresponde
        buscaParticipantes.addEventListener('input', function () {
            const consulta = normalizarBuscaParticipante(buscaParticipantes.value.trim());
            let participantesVisiveis = 0;

            // filtrar linha de participante compara a busca com as tres primeiras colunas
            linhasParticipantes.forEach(function (linha) {
                const dadosParticipante = Array.from(linha.cells)
                    .slice(0, 3)
                    // ler dado do participante retorna o texto da celula para a busca
                    .map(function (celula) { return celula.textContent; })
                    .join(' ');
                const corresponde = normalizarBuscaParticipante(dadosParticipante).includes(consulta);

                linha.hidden = !corresponde;
                // contar participantes encontrados incrementa a quantidade de linhas que correspondem a busca
                if (corresponde) participantesVisiveis++;
            });

            // mostrar busca sem participantes exibe o aviso quando nenhuma linha corresponde
            if (mensagemSemResultados) {
                mensagemSemResultados.hidden = participantesVisiveis > 0;
            }
        });
    }

    // configurar busca de conteudos prepara o envio por texto ou tipo selecionado
    document.querySelectorAll('.busca-conteudos').forEach(function (form) {
        const input = form.querySelector('input[name="q"]');
        const filtroTipo = form.querySelector('select[name="tipo"]');
        let temporizadorBusca;

        // enviar busca de conteudos cancela a espera e envia o formulario
        function enviarBusca() {
            window.clearTimeout(temporizadorBusca);
            form.requestSubmit();
        }

        // buscar conteudos ao digitar configura o intervalo de espera no campo de busca
        if (input) {
            // aguardar busca de conteudos reinicia a espera de seiscentos milissegundos ao digitar
            input.addEventListener('input', function () {
                window.clearTimeout(temporizadorBusca);
                temporizadorBusca = window.setTimeout(enviarBusca, 600);
            });
        }

        // filtrar tipo de conteudo configura a busca ao trocar o tipo selecionado
        if (filtroTipo) filtroTipo.addEventListener('change', enviarBusca);
    });

    const selectModal = document.getElementById('selecionarConteudoModal');
    let modaisAbrindo = 0;

    // contar janelas abrindo registra o inicio da abertura de uma janela
    document.addEventListener('show.bs.modal', function () {
        modaisAbrindo++;
    });

    // concluir abertura da janela reduz a quantidade de janelas em abertura
    document.addEventListener('shown.bs.modal', function () {
        modaisAbrindo = Math.max(0, modaisAbrindo - 1);
    });

    // agendar limpeza de janela aguarda o fechamento antes de liberar a pagina
    document.addEventListener('hidden.bs.modal', function () {
        // liberar pagina fechada remove o fundo e o bloqueio de rolagem quando nao ha janela aberta
        setTimeout(function () {
            // preservar janela aberta impede a limpeza enquanto alguma janela esta aberta ou abrindo
            if (modaisAbrindo > 0 || document.querySelector('.modal.show')) {
                return;
            }

            // remover fundo da janela apaga a camada que escurece a pagina
            document.querySelectorAll('.modal-backdrop').forEach(function (backdrop) {
                backdrop.remove();
            });
            document.body.classList.remove('modal-open');
            document.body.style.removeProperty('overflow');
            document.body.style.removeProperty('padding-right');
        }, 50);
    });

    // configurar selecao de conteudo associa cada botao a sua janela de edicao
    document.querySelectorAll('.conteudo-selecionar-item').forEach(function (button) {
        // selecionar conteudo fecha a lista antes de abrir a edicao escolhida
        button.addEventListener('click', function () {
            const conteudoId = this.dataset.id;
            const editModal = document.getElementById('editarConteudoModal-' + conteudoId);

            // abrir edicao de conteudo fecha a selecao antes de mostrar a janela de edicao
            if (selectModal && editModal) {
                const selectModalInstance = bootstrap.Modal.getOrCreateInstance(selectModal);
                const editModalInstance = bootstrap.Modal.getOrCreateInstance(editModal);

                // abrir edicao de conteudo exibe o formulario depois do fechamento da lista
                selectModal.addEventListener('hidden.bs.modal', function abrirEdicao() {
                    editModalInstance.show();
                }, { once: true });

                selectModalInstance.hide();
            }

        });
    });

    const participantesModal = document.getElementById('participantesTurma');
    const adicionarPessoasModal = document.getElementById('adicionarPessoasModal');
    const abrirAdicionarPessoas = document.getElementById('abrirAdicionarPessoasModal');
    let retornarAosParticipantes = false;

    // adicionar pessoas pela lista configura a troca entre as janelas de participantes e cadastro
    if (participantesModal && adicionarPessoasModal && abrirAdicionarPessoas) {
        // adicionar pessoa fecha os participantes e prepara a abertura do formulario
        abrirAdicionarPessoas.addEventListener('click', function () {
            retornarAosParticipantes = true;
            // abrir formulario de pessoa exibe a janela para adicionar participantes
            participantesModal.addEventListener('hidden.bs.modal', function abrirFormulario() {
                bootstrap.Modal.getOrCreateInstance(adicionarPessoasModal).show();
            }, { once: true });
            bootstrap.Modal.getOrCreateInstance(participantesModal).hide();
        });

        // retornar aos participantes reabre a lista ao fechar o formulario de pessoa
        adicionarPessoasModal.addEventListener('hidden.bs.modal', function () {
            // voltar aos participantes ignora o fechamento quando nao deve reabrir a lista
            if (!retornarAosParticipantes) return;

            retornarAosParticipantes = false;
            bootstrap.Modal.getOrCreateInstance(participantesModal).show();
        });
    }

    const campoAutocompletePessoa = document.querySelector('.campo-autocomplete-pessoa');
    const inputPessoa = document.getElementById('emailPessoaAdicionar');
    const listaSugestoesPessoas = document.getElementById('sugestoesPessoasAdicionar');

    // sugerir pessoas configura a busca quando o campo e a lista de sugestoes existem
    if (campoAutocompletePessoa && inputPessoa && listaSugestoesPessoas) {
        const urlSugestoes = campoAutocompletePessoa.dataset.sugestoesUrl;
        let temporizadorSugestoes;
        let requisicaoSugestoes;
        let sequenciaSugestoes = 0;

        // fechar sugestoes de pessoas cancela consultas pendentes e limpa a lista
        function fecharSugestoesPessoas() {
            window.clearTimeout(temporizadorSugestoes);
            temporizadorSugestoes = undefined;
            sequenciaSugestoes++;
            // cancelar sugestoes pendentes interrompe a consulta ao fechar a lista
            if (requisicaoSugestoes) {
                requisicaoSugestoes.abort();
                requisicaoSugestoes = null;
            }
            listaSugestoesPessoas.replaceChildren();
            listaSugestoesPessoas.hidden = true;
            inputPessoa.setAttribute('aria-expanded', 'false');
            inputPessoa.removeAttribute('aria-activedescendant');
        }

        // mostrar sugestoes de pessoas exibe as opcoes recebidas ou uma mensagem de lista vazia
        function mostrarSugestoesPessoas(pessoas) {
            listaSugestoesPessoas.replaceChildren();

            // mostrar sugestoes de pessoas exibe um aviso vazio ou cria as opcoes encontradas
            if (!pessoas.length) {
                const vazio = document.createElement('span');
                vazio.className = 'sugestao-pessoa-vazia';
                vazio.textContent = 'Nenhuma pessoa encontrada';
                listaSugestoesPessoas.appendChild(vazio);
            } else {
                // montar sugestao de pessoa mostra nome email e tipo de usuario em um botao
                pessoas.forEach(function (pessoa, indice) {
                    const opcao = document.createElement('button');
                    opcao.type = 'button';
                    opcao.className = 'sugestao-pessoa';
                    opcao.id = `sugestao-pessoa-${indice}`;
                    opcao.setAttribute('role', 'option');
                    opcao.setAttribute('aria-label', `${pessoa.nomePessoa}, ${pessoa.emailPessoa}`);

                    const nome = document.createElement('span');
                    nome.className = 'sugestao-pessoa-nome';
                    nome.textContent = pessoa.nomePessoa;

                    const detalhes = document.createElement('span');
                    detalhes.className = 'sugestao-pessoa-detalhes';
                    detalhes.textContent = `${pessoa.emailPessoa} · ${pessoa.tipoUsuario}`;

                    opcao.append(nome, detalhes);
                    // escolher pessoa copia o email para o campo e fecha as sugestoes
                    opcao.addEventListener('click', function () {
                        inputPessoa.value = pessoa.emailPessoa;
                        fecharSugestoesPessoas();
                        inputPessoa.focus();
                    });
                    listaSugestoesPessoas.appendChild(opcao);
                });
            }

            listaSugestoesPessoas.hidden = false;
            inputPessoa.setAttribute('aria-expanded', 'true');
        }

        // agendar sugestoes de pessoas cancela a consulta anterior e aguarda pelo menos duas letras
        inputPessoa.addEventListener('input', function () {
            window.clearTimeout(temporizadorSugestoes);
            // interromper busca anterior cancela a consulta de pessoas antes de iniciar outra
            if (requisicaoSugestoes) requisicaoSugestoes.abort();
            requisicaoSugestoes = null;
            const sequenciaAtual = ++sequenciaSugestoes;

            const consulta = inputPessoa.value.trim();
            // exigir termo para sugestoes fecha a lista quando o texto tem menos de dois caracteres
            if (consulta.length < 2) {
                fecharSugestoesPessoas();
                return;
            }

            // buscar sugestoes de pessoas consulta o servidor e descarta respostas de buscas antigas
            temporizadorSugestoes = window.setTimeout(async function () {
                const controller = new AbortController();
                requisicaoSugestoes = controller;
                // buscar sugestoes de pessoas tenta consultar os nomes e exibir a resposta atual
                try {
                    const resposta = await fetch(`${urlSugestoes}?q=${encodeURIComponent(consulta)}`, {
                        headers: { Accept: 'application/json' },
                        signal: controller.signal
                    });
                    // tratar falha nas sugestoes encerra a consulta quando o servidor nao retorna sucesso
                    if (!resposta.ok) {
                        // fechar sugestoes atuais limpa a lista apenas se a falha pertence a ultima busca
                        if (sequenciaAtual === sequenciaSugestoes) fecharSugestoesPessoas();
                        return;
                    }
                    const pessoas = await resposta.json();
                    // exibir sugestoes atuais usa a resposta somente se a busca ainda e a mais recente
                    if (sequenciaAtual === sequenciaSugestoes && !controller.signal.aborted) {
                        mostrarSugestoesPessoas(pessoas);
                    }
                } catch (erro) {
                    // ocultar sugestoes com erro fecha a lista quando a busca atual falha sem cancelamento
                    if (erro.name !== 'AbortError' && sequenciaAtual === sequenciaSugestoes) {
                        fecharSugestoesPessoas();
                    }
                }
            }, 180);
        });

        // navegar sugestoes de pessoas fecha com escape ou destaca a primeira opcao com a seta
        inputPessoa.addEventListener('keydown', function (evento) {
            // fechar sugestoes pelo teclado oculta a lista ao pressionar escape
            if (evento.key === 'Escape') {
                fecharSugestoesPessoas();
            }
            // navegar nas sugestoes usa a seta para baixo para acessar a primeira opcao
            else if (evento.key === 'ArrowDown' && !listaSugestoesPessoas.hidden) {
                evento.preventDefault();
                const primeiraOpcao = listaSugestoesPessoas.querySelector('.sugestao-pessoa');
                // focar primeira sugestao move o foco quando existe uma opcao
                if (primeiraOpcao) primeiraOpcao.focus();
            }
        });

        // fechar sugestoes por clique oculta a lista quando o clique ocorre fora do campo
        document.addEventListener('click', function (evento) {
            // fechar sugestoes fora do campo oculta a lista ao clicar fora da busca
            if (!campoAutocompletePessoa.contains(evento.target)) fecharSugestoesPessoas();
        });
    }

    const paginaTurma = document.querySelector('.turmas-page');

    // abrir participantes automaticamente atende a indicacao recebida na pagina da turma
    if (paginaTurma && paginaTurma.dataset.abrirModalParticipantes === 'true') {
        const modalElement = document.getElementById('participantesTurma');

        // mostrar janela de participantes abre a janela quando ela existe
        if (modalElement) {
            const modal = new bootstrap.Modal(modalElement);
            modal.show();
        }
    }

    // configurar retorno da turma agenda a atualizacao da pagina ao fechar a janela
    function recarregarTurmaAoFechar(modalElement) {
        // recarregar turma abre o endereco da turma sem os dados temporarios do formulario
        modalElement.addEventListener('hidden.bs.modal', function () {
            // manter pagina ao pedir site evita a recarga quando a janela fecha para abrir o pedido de site
            if (modalElement.dataset.trocandoParaSite === 'true') {
                delete modalElement.dataset.trocandoParaSite;
                return;
            }
            const partes = window.location.pathname.match(/\/turmas\/(\d+)/);
            // recarregar turma volta ao endereco da turma depois de fechar a janela
            if (partes) {
                window.location.replace('/turmas/' + partes[1]);
            }
        }, { once: true });
    }

    // abrir cadastro de conteudo atende a indicacao recebida na pagina da turma
    if (paginaTurma && paginaTurma.dataset.abrirModalConteudo === 'true') {
        const modalElement = document.getElementById('adicionarConteudoModal');
        // mostrar cadastro de conteudo configura a recarga e abre a janela disponivel
        if (modalElement) {
            recarregarTurmaAoFechar(modalElement);
            bootstrap.Modal.getOrCreateInstance(modalElement).show();
        }
    }

    // abrir edicao da turma atende a indicacao recebida na pagina
    if (paginaTurma && paginaTurma.dataset.abrirModalEditarTurma === 'true') {
        const modalElement = document.getElementById('editarTurmaModal');
        // mostrar edicao da turma abre a janela quando ela existe
        if (modalElement) {
            bootstrap.Modal.getOrCreateInstance(modalElement).show();
        }
    }

    // abrir edicao de material procura a janela do conteudo indicado pela pagina
    if (paginaTurma && paginaTurma.dataset.abrirModalEditarId) {
        const modalEditar = document.getElementById('editarConteudoModal-' + paginaTurma.dataset.abrirModalEditarId);
        // mostrar edicao do material configura a recarga e abre a janela encontrada
        if (modalEditar) {
            recarregarTurmaAoFechar(modalEditar);
            bootstrap.Modal.getOrCreateInstance(modalEditar).show();
        }
    }

    const formExcluirTurma = document.querySelector('.form-excluir-turma');

    // confirmar delete turma configura a confirmacao antes de enviar o formulario
    if (formExcluirTurma) {
        // confirmar delete turma pede confirmacao antes de excluir a turma e seus vinculos
        formExcluirTurma.addEventListener('submit', function (event) {
            const confirmar = confirm(
                'Tem certeza que deseja excluir esta turma? Isso removerá também todos os alunos e professores vinculados a ela.'
            );

            // cancelar acao nao confirmada impede o envio quando a pessoa recusa a confirmacao
            if (!confirmar) {
                event.preventDefault();
            }
        });
    }

    // configurar remocao de participante prepara a confirmacao de cada formulario
    document.querySelectorAll('.form-remover-participante').forEach(function (form) {
        // confirmar delete participante pede confirmacao com o nome da pessoa antes de remover o vinculo
        form.addEventListener('submit', function (event) {
            const nome = form.dataset.nome || 'este participante';
            const confirmar = confirm(
                `Tem certeza que deseja remover ${nome} desta turma? Esta ação não pode ser desfeita.`
            );

            // cancelar acao nao confirmada impede o envio quando a pessoa recusa a confirmacao
            if (!confirmar) {
                event.preventDefault();
            }
        });
    });

    // configurar confirmacao de formulario associa a mensagem definida na pagina ao envio
    document.querySelectorAll('form[data-confirmar]').forEach(function (form) {
        // confirmar acao do formulario impede o envio quando a pessoa cancela a mensagem
        form.addEventListener('submit', function (event) {
            const confirmar = confirm(form.dataset.confirmar);

            // cancelar acao nao confirmada impede o envio quando a pessoa recusa a confirmacao
            if (!confirmar) {
                event.preventDefault();
            }
        });
    });
});


// escapar texto de resultado protege caracteres especiais antes de montar o resultado da busca
function escaparTextoResultado(valor) {
    const texto = document.createElement('span');
    texto.textContent = valor == null ? '' : String(valor);
    return texto.innerHTML.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// mostrar previa do conteudo monta a imagem o titulo e os detalhes do item escolhido
function mostrarPreviaConteudo(container, titulo, detalhes, thumbnailUrl, tipo) {
    const previa = document.createElement('div');
    previa.className = 'conteudo-selecionado-preview';
    previa.setAttribute('role', 'status');

    // mostrar previa do conteudo inclui a imagem quando existe uma miniatura
    if (thumbnailUrl) {
        const thumbnail = document.createElement('img');
        thumbnail.className = 'conteudo-selecionado-thumbnail' +
            (tipo === 'livro' ? ' conteudo-selecionado-thumbnail-livro' : '');
        thumbnail.src = thumbnailUrl;
        thumbnail.alt = '';
        previa.appendChild(thumbnail);
    }
    // mostrar livro sem capa cria o espaco da capa quando o livro nao possui imagem
    else if (tipo === 'livro') {
        const capaVazia = document.createElement('div');
        capaVazia.className = 'conteudo-selecionado-thumbnail conteudo-selecionado-thumbnail-livro-vazia';
        capaVazia.setAttribute('aria-hidden', 'true');
        previa.appendChild(capaVazia);
    }

    const textos = document.createElement('div');
    textos.className = 'conteudo-selecionado-textos';

    const tituloElemento = document.createElement('div');
    tituloElemento.className = 'conteudo-selecionado-titulo';
    tituloElemento.textContent = titulo;
    tituloElemento.title = titulo;

    const detalhesElemento = document.createElement('div');
    detalhesElemento.className = 'conteudo-selecionado-detalhes';
    detalhesElemento.textContent = detalhes;

    textos.append(tituloElemento, detalhesElemento);
    previa.appendChild(textos);
    container.replaceChildren(previa);
}

// ler resposta de livros retorna os dados ou usa a mensagem de erro recebida
async function lerRespostaBuscaLivros(resposta) {
    let corpo;
    // ler busca de livros tenta interpretar a resposta e preservar a mensagem de falha
    try {
        corpo = await resposta.json();
    } catch (erro) {
        // avisar falha na busca de livros informa indisponibilidade quando a resposta nao pode ser lida
        if (!resposta.ok) {
            throw new Error('Não foi possível consultar o Google Books. Tente novamente.');
        }
        throw erro;
    }

    // conferir resposta de livros usa a mensagem do servidor quando a consulta falha
    if (!resposta.ok) {
        throw new Error(corpo.message || 'Não foi possível consultar o Google Books. Tente novamente.');
    }

    return corpo;
}

// preparar contadores de descricao configura a contagem nos formularios de cadastro e edicao
document.addEventListener('DOMContentLoaded', function () {

    // configurar contador de descricao atualiza o total inicial e acompanha a digitacao
    function ligarContador(textarea, contador) {
        // configurar contador de texto exige o campo e o elemento do contador
        if (!textarea || !contador) return;

        // contar caracteres da descricao mostra o tamanho atual do texto
        function atualizar() {
            contador.textContent = textarea.value.length;
        }

        textarea.addEventListener('input', atualizar);
        atualizar();
    }

    ligarContador(
        document.getElementById('descricao'),
        document.getElementById('contadorDescricaoAdicionar')
    );

    // localizar contador de edicao associa a descricao ao contador da mesma janela
    document.querySelectorAll('textarea[name="descricao"]').forEach(function (textarea) {
        const modal = textarea.closest('.modal');
        // configurar cadastro de conteudo encerra quando a janela nao existe
        if (!modal) return;
        const contador = modal.querySelector('[id^="contadorDescricaoEditar-"]');
        ligarContador(textarea, contador);
    });
});

// preparar cadastro de conteudo configura os campos por tipo e a busca de videos
document.addEventListener('DOMContentLoaded', function () {

    const selectTipo = document.getElementById('tipoConteudo');
    const campoLink = document.getElementById('campoLink');
    const campoYoutube = document.getElementById('campoYoutube');
    const campoGoogleBooks = document.getElementById('campoGoogleBooks');
    const campoArquivo = document.getElementById('campoArquivo');

    // alternar campos do cadastro mostra apenas os campos do tipo de conteudo escolhido
    function atualizarCamposVisiveis() {
        // alternar campos do conteudo encerra quando o seletor de tipo nao existe
        if (!selectTipo) return;

        const tipo = selectTipo.value;

        campoLink.style.display = (tipo === 'LINK') ? '' : 'none';
        campoYoutube.style.display = (tipo === 'YOUTUBE') ? '' : 'none';
        campoGoogleBooks.style.display = (tipo === 'GOOGLE_BOOKS') ? '' : 'none';
        campoArquivo.style.display = (tipo === 'ARQUIVO') ? '' : 'none';
    }

    // iniciar tipo de conteudo seleciona link e configura a troca dos campos
    if (selectTipo) {
        selectTipo.value = 'LINK';
        selectTipo.addEventListener('change', atualizarCamposVisiveis);
        atualizarCamposVisiveis();
    }


    const inputBuscaYoutube = document.getElementById('buscaYoutube');
    const resultadosYoutube = document.getElementById('resultadosYoutube');
    const inputUrl = document.getElementById('url');
    const inputTitulo = document.getElementById('titulo');
    const inputDuracao = document.getElementById('duracaoEstimada');
    let nextPageTokenYoutube = null;
    let termoYoutubeAtual = '';
    let sequenciaBuscaYoutube = 0;
    let carregandoVideos = false;

    // buscar videos para cadastro consulta o termo e controla o carregamento de novas paginas
    function buscarVideos(carregarMais) {
        const termo = inputBuscaYoutube.value.trim();
        // ignorar busca vazia encerra a pesquisa quando nao existe texto para consultar
        if (!termo) return;

        // paginar busca de videos valida a proxima pagina ou inicia uma nova busca
        if (carregarMais) {
            // bloquear pagina de videos ignora pedido sem proxima pagina com busca ativa ou termo diferente
            if (!nextPageTokenYoutube || carregandoVideos || termo !== termoYoutubeAtual) return;
        } else {
            termoYoutubeAtual = termo;
            nextPageTokenYoutube = null;
            sequenciaBuscaYoutube++;
        }

        const sequenciaAtual = sequenciaBuscaYoutube;
        carregandoVideos = true;
        const botaoCarregarMais = resultadosYoutube.querySelector('.carregar-mais-resultados');
        // mostrar carregamento de videos desabilita o botao de mais resultados ou mostra o aviso inicial
        if (carregarMais && botaoCarregarMais) {
            botaoCarregarMais.disabled = true;
            botaoCarregarMais.textContent = 'Carregando...';
        } else {
            resultadosYoutube.innerHTML = '<p class="form-text">Buscando...</p>';
        }

        const parametros = new URLSearchParams({ q: termo });
        // buscar proxima pagina de videos inclui o codigo de continuacao da busca
        if (carregarMais) parametros.set('pageToken', nextPageTokenYoutube);

        fetch('/youtube/buscar?' + parametros.toString())
            // ler resposta de videos rejeita falhas e interpreta os dados recebidos
            .then(function (resposta) {
                // conferir busca de videos informa falha quando a consulta nao tem sucesso
                if (!resposta.ok) throw new Error('Falha na busca');
                // ler pagina de videos junta os resultados com o codigo da proxima pagina
                return resposta.json().then(function (videos) {
                    return {
                        videos: videos,
                        nextPageToken: resposta.headers.get('X-Next-Page-Token')
                    };
                });
            })
            // mostrar videos encontrados descarta buscas antigas e prepara a proxima pagina
            .then(function (pagina) {
                // descartar busca antiga de videos ignora a resposta quando a sequencia ou o termo mudou
                if (sequenciaAtual !== sequenciaBuscaYoutube ||
                    inputBuscaYoutube.value.trim() !== termo) return;
                // renovar resultados de videos limpa a lista apenas ao iniciar uma nova busca
                if (!carregarMais) resultadosYoutube.innerHTML = '';
                // delete botao antigo de videos retira o botao antes de acrescentar a nova pagina
                else if (botaoCarregarMais) botaoCarregarMais.remove();

                // mostrar busca sem videos exibe o aviso quando a primeira pagina esta vazia
                if (!pagina.videos.length && !carregarMais) {
                    resultadosYoutube.innerHTML = '<p class="form-text">Nenhum vídeo encontrado.</p>';
                    return;
                }

                // montar resultado de video exibe imagem titulo canal e duracao com selecao por clique
                pagina.videos.forEach(function (video) {
                    const item = document.createElement('div');
                    item.className = 'resultado-youtube-item';
                    item.style.cssText = 'display:flex; gap:10px; align-items:center; padding:8px; border:1px solid #ddd; border-radius:8px; margin-bottom:6px; cursor:pointer;';

                    item.innerHTML =
                        '<img src="' + escaparTextoResultado(video.thumbnailUrl) + '" style="width:80px; border-radius:4px;">' +
                        '<div class="resultado-conteudo-textos">' +
                        '<div class="resultado-conteudo-titulo" style="font-weight:600; font-size:14px;">' + escaparTextoResultado(video.titulo) + '</div>' +
                        '<div style="font-size:12px; color:#666;">' + escaparTextoResultado(video.canal) + ' · ' + escaparTextoResultado(video.duracao) + '</div>' +
                        '</div>';
                    item.querySelector('.resultado-conteudo-titulo').title = video.titulo;

                    // selecionar video para cadastro preenche link titulo e duracao e mostra a previa
                    item.addEventListener('click', function () {
                        inputUrl.value = 'https://www.youtube.com/watch?v=' + video.videoId;
                        // preencher titulo pelo video usa o titulo escolhido quando o campo ainda esta vazio
                        if (inputTitulo && !inputTitulo.value.trim()) {
                            inputTitulo.value = video.titulo;
                        }
                        // preencher duracao pelo video usa a duracao do video selecionado
                        if (inputDuracao) {
                            inputDuracao.value = video.duracao;
                        }
                        mostrarPreviaConteudo(
                            resultadosYoutube,
                            video.titulo,
                            [video.canal, video.duracao].filter(Boolean).join(' · '),
                            video.thumbnailUrl,
                            'video'
                        );
                    });

                    resultadosYoutube.appendChild(item);
                });

                nextPageTokenYoutube = pagina.nextPageToken;
                // oferecer mais videos cria o botao quando existe uma proxima pagina
                if (nextPageTokenYoutube) {
                    const botao = document.createElement('button');
                    botao.type = 'button';
                    botao.className = 'carregar-mais-resultados';
                    botao.textContent = 'Carregar mais vídeos';
                    // carregar mais videos solicita a proxima pagina da busca atual
                    botao.addEventListener('click', function () { buscarVideos(true); });
                    resultadosYoutube.appendChild(botao);
                }
            })
            // tratar erro na busca de videos oferece nova tentativa ou mostra a falha
            .catch(function () {
                // tratar erro da busca atual de videos atualiza a tela apenas se o termo e a sequencia continuam atuais
                if (sequenciaAtual === sequenciaBuscaYoutube &&
                    inputBuscaYoutube.value.trim() === termo) {
                    // permitir nova tentativa de videos reativa o botao de mais resultados ou mostra o erro inicial
                    if (carregarMais && botaoCarregarMais) {
                        botaoCarregarMais.disabled = false;
                        botaoCarregarMais.textContent = 'Tentar carregar mais vídeos';
                    } else {
                        resultadosYoutube.innerHTML = '<p class="erro-campo">Erro ao buscar vídeos. Tente novamente.</p>';
                    }
                }
            })
            // concluir busca de videos libera outra consulta quando a busca atual termina
            .finally(function () {
                // finalizar busca de videos libera o carregamento somente para a busca atual
                if (sequenciaAtual === sequenciaBuscaYoutube) carregandoVideos = false;
            });
    }

    configurarBuscaAutomatica(inputBuscaYoutube, buscarVideos, resultadosYoutube);

    // configurar teclado da busca de videos adiciona o atalho quando o campo existe
    if (inputBuscaYoutube) {
        // buscar videos com enter impede o envio do formulario e executa a busca
        inputBuscaYoutube.addEventListener('keydown', function (evento) {
            // buscar videos com enter inicia a busca e impede o envio do formulario
            if (evento.key === 'Enter') {
                evento.preventDefault();
                buscarVideos(false);
            }
        });
    }
});

// preparar edicao e livros configura os tipos de conteudo e a busca de livros para cadastro
document.addEventListener('DOMContentLoaded', function () {

    // configurar edicao de conteudo prepara campos e buscas de videos e livros de cada janela
    document.querySelectorAll('.tipo-conteudo-editar').forEach(function (selectTipo) {
        const modalBody = selectTipo.closest('.modal-body');
        // configurar edicao de conteudo encerra quando o corpo da janela nao existe
        if (!modalBody) return;

        const campoLinkEditar = modalBody.querySelector('.campo-link-editar');
        const campoYoutubeEditar = modalBody.querySelector('.campo-youtube-editar');
        const campoGoogleBooks = modalBody.querySelector('.campo-google-books-editar');
        const campoArquivoEditar = modalBody.querySelector('.campo-arquivo-editar');
        const inputUrlEditar = campoLinkEditar.querySelector('input[name="url"]');
        const inputDuracaoEditar = modalBody.querySelector('input[name="duracaoEstimada"]');

        // alternar campos da edicao mostra os campos correspondentes ao tipo selecionado
        function atualizarCampos() {
            const tipo = selectTipo.value;
            campoLinkEditar.style.display = (tipo === 'LINK') ? '' : 'none';
            campoYoutubeEditar.style.display = (tipo === 'YOUTUBE') ? '' : 'none';
            campoGoogleBooks.style.display = (tipo === 'GOOGLE_BOOKS') ? '' : 'none';
            campoArquivoEditar.style.display = (tipo === 'ARQUIVO') ? '' : 'none';
        }

        selectTipo.value = 'LINK';
        selectTipo.addEventListener('change', atualizarCampos);
        atualizarCampos();


        const inputBuscaYoutubeEditar = campoYoutubeEditar.querySelector('.busca-youtube-editar');
        const resultadosYoutubeEditar = campoYoutubeEditar.querySelector('.resultados-youtube-editar');

        // buscar videos para edicao consulta o termo informado e mostra o carregamento
        function buscarVideosEditar() {
            const termo = inputBuscaYoutubeEditar.value.trim();
            // ignorar busca vazia encerra a pesquisa quando nao existe texto para consultar
            if (!termo) return;

            resultadosYoutubeEditar.innerHTML = '<p class="form-text">Buscando...</p>';

            fetch('/youtube/buscar?q=' + encodeURIComponent(termo))
                // ler videos da edicao retorna os dados recebidos ou interrompe em caso de falha
                .then(function (resposta) {
                    // conferir videos na edicao informa falha quando a busca nao tem sucesso
                    if (!resposta.ok) throw new Error('Falha na busca');
                    return resposta.json();
                })
                // mostrar videos da edicao descarta buscas antigas e exibe os resultados encontrados
                .then(function (videos) {
                    // descartar videos antigos da edicao ignora a resposta de um termo que ja mudou
                    if (inputBuscaYoutubeEditar.value.trim() !== termo) return;
                    resultadosYoutubeEditar.innerHTML = '';

                    // mostrar edicao sem videos avisa quando a busca nao encontra resultados
                    if (videos.length === 0) {
                        resultadosYoutubeEditar.innerHTML = '<p class="form-text">Nenhum vídeo encontrado.</p>';
                        return;
                    }

                    // montar video para edicao mostra imagem titulo canal e duracao com escolha por clique
                    videos.forEach(function (video) {
                        const item = document.createElement('div');
                        item.style.cssText = 'display:flex; gap:10px; align-items:center; padding:8px; border:1px solid #ddd; border-radius:8px; margin-bottom:6px; cursor:pointer;';

                        item.innerHTML =
                            '<img src="' + escaparTextoResultado(video.thumbnailUrl) + '" style="width:80px; border-radius:4px;">' +
                            '<div>' +
                            '<div style="font-weight:600; font-size:14px;">' + escaparTextoResultado(video.titulo) + '</div>' +
                            '<div style="font-size:12px; color:#666;">' + escaparTextoResultado(video.canal) + ' · ' + escaparTextoResultado(video.duracao) + '</div>' +
                            '</div>';

                        // selecionar video para edicao preenche o link e a duracao do conteudo
                        item.addEventListener('click', function () {
                            inputUrlEditar.value = 'https://www.youtube.com/watch?v=' + video.videoId;
                            // preencher duracao na edicao usa a duracao do video selecionado
                            if (inputDuracaoEditar) inputDuracaoEditar.value = video.duracao;
                            resultadosYoutubeEditar.innerHTML = '<p class="form-text">Selecionado: ' + escaparTextoResultado(video.titulo) + '</p>';
                        });

                        resultadosYoutubeEditar.appendChild(item);
                    });
                })
                // mostrar erro de videos na edicao informa a falha apenas para o termo atual
                .catch(function () {
                    // avisar erro de videos na edicao mostra a falha apenas para o termo atual
                    if (inputBuscaYoutubeEditar.value.trim() === termo) {
                        resultadosYoutubeEditar.innerHTML = '<p class="erro-campo">Erro ao buscar vídeos. Tente novamente.</p>';
                    }
                });
        }

        configurarBuscaAutomatica(inputBuscaYoutubeEditar, buscarVideosEditar, resultadosYoutubeEditar);

        // configurar busca de videos na edicao habilita o atalho quando o campo existe
        if (inputBuscaYoutubeEditar) {
            // buscar videos da edicao com enter executa a pesquisa sem enviar o formulario
            inputBuscaYoutubeEditar.addEventListener('keydown', function (evento) {
                // buscar videos na edicao com enter inicia a busca sem enviar o formulario
                if (evento.key === 'Enter') {
                    evento.preventDefault();
                    buscarVideosEditar();
                }
            });
        }


        const inputBuscaBooksEditar = campoGoogleBooks.querySelector('.busca-google-books-editar');
        const resultadosBooksEditar = campoGoogleBooks.querySelector('.resultados-google-books-editar');

        // buscar livros para edicao consulta o termo e prepara os resultados ou a mensagem de erro
        function buscarLivrosEditar() {
            const termo = inputBuscaBooksEditar.value.trim();
            // ignorar busca vazia encerra a pesquisa quando nao existe texto para consultar
            if (!termo) return;

            resultadosBooksEditar.innerHTML = '<p class="form-text">Buscando...</p>';

            fetch('/googlebooks/buscar?q=' + encodeURIComponent(termo))
                .then(lerRespostaBuscaLivros)
                // mostrar livros da edicao ignora buscas antigas e apresenta os livros encontrados
                .then(function (livros) {
                    // descartar livros antigos da edicao ignora a resposta de um termo que ja mudou
                    if (inputBuscaBooksEditar.value.trim() !== termo) return;
                    resultadosBooksEditar.innerHTML = '';

                    // mostrar edicao sem livros avisa quando a busca nao encontra resultados
                    if (livros.length === 0) {
                        resultadosBooksEditar.innerHTML = '<p class="form-text">Nenhum livro encontrado.</p>';
                        return;
                    }

                    // montar livro para edicao mostra capa titulo autores e ano com escolha por clique
                    livros.forEach(function (livro) {
                        const item = document.createElement('div');
                        item.style.cssText = 'display:flex; gap:10px; align-items:center; padding:8px; border:1px solid #ddd; border-radius:8px; margin-bottom:6px; cursor:pointer;';

                        const capa = livro.thumbnailUrl
                            ? '<img src="' + escaparTextoResultado(livro.thumbnailUrl) + '" style="width:50px; border-radius:4px;">'
                            : '<div style="width:50px; height:70px; background:#eee; border-radius:4px;"></div>';

                        item.innerHTML =
                            capa +
                            '<div>' +
                            '<div style="font-weight:600; font-size:14px;">' + escaparTextoResultado(livro.titulo) + '</div>' +
                            '<div style="font-size:12px; color:#666;">' + escaparTextoResultado(livro.autores) + ' · ' + escaparTextoResultado(livro.anoPublicacao) + '</div>' +
                            '</div>';

                        // selecionar livro para edicao preenche o link e mostra o titulo escolhido
                        item.addEventListener('click', function () {
                            inputUrlEditar.value = livro.linkGoogleBooks;
                            resultadosBooksEditar.innerHTML = '<p class="form-text">Selecionado: ' + escaparTextoResultado(livro.titulo) + '</p>';
                        });

                        resultadosBooksEditar.appendChild(item);
                    });
                })
                // mostrar erro de livros na edicao apresenta a falha da busca atual com texto protegido
                .catch(function (erro) {
                    // avisar erro de livros na edicao mostra a falha apenas para o termo atual
                    if (inputBuscaBooksEditar.value.trim() === termo) {
                        resultadosBooksEditar.innerHTML =
                            '<p class="erro-campo">' +
                            escaparTextoResultado(erro.message || 'Erro ao buscar livros. Tente novamente.') +
                            '</p>';
                    }
                });
        }

        configurarBuscaAutomatica(inputBuscaBooksEditar, buscarLivrosEditar, resultadosBooksEditar);

        // configurar busca de livros na edicao habilita o atalho quando o campo existe
        if (inputBuscaBooksEditar) {
            // buscar livros da edicao com enter executa a pesquisa sem enviar o formulario
            inputBuscaBooksEditar.addEventListener('keydown', function (evento) {
                // buscar livros na edicao com enter inicia a busca sem enviar o formulario
                if (evento.key === 'Enter') {
                    evento.preventDefault();
                    buscarLivrosEditar();
                }
            });
        }
    });


    const inputBuscaBooks = document.getElementById('buscaGoogleBooks');
    const resultadosBooks = document.getElementById('resultadosGoogleBooks');
    const inputUrlAdicionar = document.getElementById('url');
    const inputTituloAdicionar = document.getElementById('titulo');
    let nextStartIndexBooks = null;
    let termoBooksAtual = '';
    let sequenciaBuscaBooks = 0;
    let carregandoLivros = false;

    // buscar livros para cadastro controla a pesquisa e o carregamento de paginas adicionais
    function buscarLivros(carregarMais) {
        const termo = inputBuscaBooks.value.trim();
        // ignorar busca vazia encerra a pesquisa quando nao existe texto para consultar
        if (!termo) return;

        // paginar busca de livros valida a continuacao ou inicia uma nova busca
        if (carregarMais) {
            // bloquear pagina de livros ignora pedido sem proxima pagina com busca ativa ou termo diferente
            if (nextStartIndexBooks === null || carregandoLivros || termo !== termoBooksAtual) return;
        } else {
            termoBooksAtual = termo;
            nextStartIndexBooks = null;
            sequenciaBuscaBooks++;
        }

        const sequenciaAtual = sequenciaBuscaBooks;
        carregandoLivros = true;
        const botaoCarregarMais = resultadosBooks.querySelector('.carregar-mais-resultados');
        // mostrar carregamento de livros desabilita o botao de mais resultados ou mostra o aviso inicial
        if (carregarMais && botaoCarregarMais) {
            botaoCarregarMais.disabled = true;
            botaoCarregarMais.textContent = 'Carregando...';
        } else {
            resultadosBooks.innerHTML = '<p class="form-text">Buscando...</p>';
        }

        const parametros = new URLSearchParams({ q: termo });
        // buscar proxima pagina de livros inclui a posicao inicial da proxima pagina
        if (carregarMais) parametros.set('startIndex', nextStartIndexBooks);

        fetch('/googlebooks/buscar?' + parametros.toString())
            // ler resposta de livros do cadastro prepara os resultados e a proxima pagina
            .then(function (resposta) {
                // ler pagina de livros junta os livros ao indice recebido para continuar a busca
                return lerRespostaBuscaLivros(resposta).then(function (livros) {
                    return {
                        livros: livros,
                        nextStartIndex: resposta.headers.get('X-Next-Start-Index')
                    };
                });
            })
            // mostrar livros encontrados descarta consultas antigas e prepara o botao de mais resultados
            .then(function (pagina) {
                // descartar busca antiga de livros ignora a resposta quando a sequencia ou o termo mudou
                if (sequenciaAtual !== sequenciaBuscaBooks ||
                    inputBuscaBooks.value.trim() !== termo) return;
                // renovar resultados de livros limpa a lista apenas ao iniciar uma nova busca
                if (!carregarMais) resultadosBooks.innerHTML = '';
                // delete botao antigo de livros retira o botao antes de acrescentar a nova pagina
                else if (botaoCarregarMais) botaoCarregarMais.remove();

                // mostrar busca sem livros exibe o aviso quando a primeira pagina esta vazia
                if (!pagina.livros.length && !carregarMais) {
                    resultadosBooks.innerHTML = '<p class="form-text">Nenhum livro encontrado.</p>';
                    return;
                }

                // montar resultado de livro mostra capa titulo autores e ano com selecao por clique
                pagina.livros.forEach(function (livro) {
                    const item = document.createElement('div');
                    item.style.cssText = 'display:flex; gap:10px; align-items:center; padding:8px; border:1px solid #ddd; border-radius:8px; margin-bottom:6px; cursor:pointer;';

                    const capa = livro.thumbnailUrl
                        ? '<img src="' + escaparTextoResultado(livro.thumbnailUrl) + '" style="width:50px; border-radius:4px;">'
                        : '<div style="width:50px; height:70px; background:#eee; border-radius:4px;"></div>';

                    item.innerHTML =
                        capa +
                        '<div class="resultado-conteudo-textos">' +
                        '<div class="resultado-conteudo-titulo" style="font-weight:600; font-size:14px;">' + escaparTextoResultado(livro.titulo) + '</div>' +
                        '<div style="font-size:12px; color:#666;">' + escaparTextoResultado(livro.autores) + ' · ' + escaparTextoResultado(livro.anoPublicacao) + '</div>' +
                        '</div>';
                    item.querySelector('.resultado-conteudo-titulo').title = livro.titulo;

                    // selecionar livro para cadastro preenche link e titulo e mostra a previa do livro
                    item.addEventListener('click', function () {
                        inputUrlAdicionar.value = livro.linkGoogleBooks;
                        // preencher titulo pelo livro usa o titulo escolhido quando o campo ainda esta vazio
                        if (inputTituloAdicionar && !inputTituloAdicionar.value.trim()) {
                            inputTituloAdicionar.value = livro.titulo;
                        }
                        mostrarPreviaConteudo(
                            resultadosBooks,
                            livro.titulo,
                            [livro.autores, livro.anoPublicacao].filter(Boolean).join(' · '),
                            livro.thumbnailUrl,
                            'livro'
                        );
                    });

                    resultadosBooks.appendChild(item);
                });

                nextStartIndexBooks = pagina.nextStartIndex === null
                    ? null
                    : Number(pagina.nextStartIndex);
                // oferecer mais livros cria o botao quando existe uma posicao valida para a proxima pagina
                if (nextStartIndexBooks !== null && Number.isFinite(nextStartIndexBooks)) {
                    const botao = document.createElement('button');
                    botao.type = 'button';
                    botao.className = 'carregar-mais-resultados';
                    botao.textContent = 'Carregar mais livros';
                    // carregar mais livros solicita a proxima pagina da pesquisa atual
                    botao.addEventListener('click', function () { buscarLivros(true); });
                    resultadosBooks.appendChild(botao);
                }
            })
            // tratar erro na busca de livros mostra a falha ou libera uma nova tentativa
            .catch(function (erro) {
                // tratar erro da busca atual de livros atualiza a tela apenas se o termo e a sequencia continuam atuais
                if (sequenciaAtual === sequenciaBuscaBooks &&
                    inputBuscaBooks.value.trim() === termo) {
                    // permitir nova tentativa de livros reativa o botao de mais resultados ou mostra o erro inicial
                    if (carregarMais && botaoCarregarMais) {
                        botaoCarregarMais.disabled = false;
                        botaoCarregarMais.textContent = 'Tentar carregar mais livros';
                        botaoCarregarMais.title = erro.message || 'Erro ao carregar mais livros.';
                    } else {
                        resultadosBooks.innerHTML =
                            '<p class="erro-campo">' +
                            escaparTextoResultado(erro.message || 'Erro ao buscar livros. Tente novamente.') +
                            '</p>';
                    }
                }
            })
            // concluir busca de livros libera outra consulta quando a busca atual termina
            .finally(function () {
                // finalizar busca de livros libera o carregamento somente para a busca atual
                if (sequenciaAtual === sequenciaBuscaBooks) carregandoLivros = false;
            });
    }

    configurarBuscaAutomatica(inputBuscaBooks, buscarLivros, resultadosBooks);

    // configurar teclado da busca de livros adiciona o atalho quando o campo existe
    if (inputBuscaBooks) {
        // buscar livros com enter executa a pesquisa sem enviar o cadastro
        inputBuscaBooks.addEventListener('keydown', function (evento) {
            // buscar livros com enter inicia a busca e impede o envio do formulario
            if (evento.key === 'Enter') {
                evento.preventDefault();
                buscarLivros(false);
            }
        });
    }
});

// pedido de site mostra qual site sera liberado e abre o pedido a partir dos formularios de material
document.addEventListener('DOMContentLoaded', function () {
    const modalSite = document.getElementById('solicitarDominioModal');
    const campoSite = document.getElementById('dominioSolicitado');
    const previa = document.getElementById('previaSiteSolicitado');

    // exigir pedido de site ignora a configuracao para quem nao e professor
    if (!modalSite || !campoSite) return;

    const SITE_VALIDO = /^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;

    // extrair site do link este metodo repete a limpeza feita pelo servidor ao receber o pedido
    function extrairSite(texto) {
        let site = texto.trim().toLowerCase();
        site = site.replace(/^[a-z][a-z0-9+.-]*:\/\//, '');
        site = site.replace(/[/?#].*$/, '');
        // recusar usuario no link avisa que o endereco nao pode ter usuario ou senha
        if (site.includes('@')) return { erro: 'Informe apenas o link do site, sem usuário ou senha.' };
        site = site.replace(/:\d+$/, '').replace(/^www\./, '').replace(/\.$/, '');
        // conferir formato do site informa quando o texto ainda nao parece um site
        if (!SITE_VALIDO.test(site)) return { erro: 'Ainda não parece um link de site. Ex: https://www.exemplo.com.br' };
        return { site: site };
    }

    // atualizar previa este metodo mostra o site que sera liberado enquanto o professor digita
    function atualizarPrevia() {
        // exigir area da previa ignora a atualizacao quando ela nao existe
        if (!previa) return;
        const texto = campoSite.value.trim();
        previa.replaceChildren();
        // esconder previa vazia oculta a mensagem quando o campo esta em branco
        if (!texto) {
            previa.hidden = true;
            return;
        }
        const resultado = extrairSite(texto);
        // mostrar resultado exibe o site encontrado ou a orientacao de formato
        if (resultado.site) {
            const destaque = document.createElement('strong');
            destaque.textContent = resultado.site;
            previa.append('Será liberado: ', destaque, ' (vale para todas as páginas do site)');
            previa.classList.remove('text-danger');
        } else {
            previa.textContent = resultado.erro;
            previa.classList.add('text-danger');
        }
        previa.hidden = false;
    }

    campoSite.addEventListener('input', atualizarPrevia);

    // abrir pedido pelo material copia o link digitado e troca a janela do material pela do pedido de site
    document.querySelectorAll('.solicitar-site-atalho').forEach(function (botao) {
        botao.addEventListener('click', function () {
            const areaLink = botao.closest('.campo-link-editar, #campoLink');
            const campoLink = areaLink ? areaLink.querySelector('input') : null;
            const modalAtual = botao.closest('.modal');

            // preencher pedido usa o link do material quando o professor ja digitou um
            if (campoLink && campoLink.value.trim()) {
                campoSite.value = campoLink.value.trim();
            }
            atualizarPrevia();

            const abrirPedido = function () {
                bootstrap.Modal.getOrCreateInstance(modalSite).show();
            };
            // trocar janela fecha o material antes de abrir o pedido para nao empilhar janelas
            if (modalAtual) {
                modalAtual.dataset.trocandoParaSite = 'true';
                modalAtual.addEventListener('hidden.bs.modal', abrirPedido, { once: true });
                bootstrap.Modal.getOrCreateInstance(modalAtual).hide();
            } else {
                abrirPedido();
            }
        });
    });
});
