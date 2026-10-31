function configurarBuscaAutomatica(input, buscar, resultados) {
    if (!input || !resultados) return;

    let temporizadorBusca;
    input.addEventListener('input', function () {
        window.clearTimeout(temporizadorBusca);
        resultados.replaceChildren();

        if (!input.value.trim()) {
            return;
        }

        temporizadorBusca = window.setTimeout(buscar, 500);
    });
}

document.addEventListener('DOMContentLoaded', function () {
    const itensLivro = Array.from(document.querySelectorAll('.conteudo-midia[data-book-id]'))
        .filter(function (item) { return item.dataset.bookId; });
    const itensPorLivroId = new Map();

    itensLivro.forEach(function (item) {
        const itens = itensPorLivroId.get(item.dataset.bookId) || [];
        itens.push(item);
        itensPorLivroId.set(item.dataset.bookId, itens);
    });

    async function carregarDetalhesLivro(volumeId) {
        const parametros = new URLSearchParams({ id: volumeId });
        const resposta = await fetch('/googlebooks/detalhes?' + parametros.toString(), {
            headers: { Accept: 'application/json' }
        });
        if (!resposta.ok) {
            let mensagem = 'Não foi possível carregar os dados do livro.';
            try {
                const erro = await resposta.json();
                mensagem = erro.message || mensagem;
            } catch (erroLeitura) {
                // Usa a mensagem padrão quando a resposta de erro não contém JSON.
            }
            throw new Error(mensagem);
        }

        return resposta.json();
    }

    async function carregarLoteLivros(volumeIds) {
        const resultados = await Promise.allSettled(volumeIds.map(carregarDetalhesLivro));

        resultados.forEach(function (resultado, indice) {
            const volumeId = volumeIds[indice];
            const itens = itensPorLivroId.get(volumeId) || [];

            if (resultado.status === 'rejected') {
                console.warn('Não foi possível carregar os detalhes do livro.', resultado.reason);
                itens.forEach(function (item) {
                    const titulo = item.querySelector('.conteudo-midia-livro-titulo');
                    const autor = item.querySelector('.conteudo-midia-livro-autor');
                    if (titulo) titulo.textContent = 'Informações do livro indisponíveis.';
                    if (autor) autor.textContent = '';
                });
                return;
            }

            const livro = resultado.value;
            itens.forEach(function (item) {
                const titulo = item.querySelector('.conteudo-midia-livro-titulo');
                const autor = item.querySelector('.conteudo-midia-livro-autor');
                const imagem = item.querySelector('.conteudo-midia-link img');
                const link = item.querySelector('.conteudo-midia-link');

                if (titulo) {
                    titulo.textContent = livro.titulo || 'Livro do Google Books';
                    titulo.title = livro.titulo || '';
                }
                if (autor) {
                    autor.textContent = [livro.autores, livro.anoPublicacao]
                        .filter(Boolean)
                        .join(' · ');
                }
                if (link && livro.titulo) {
                    link.setAttribute('aria-label', 'Abrir livro: ' + livro.titulo);
                }
                if (imagem && livro.thumbnailUrl) {
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
    async function carregarDetalhesLivros() {
        for (let inicio = 0; inicio < idsLivros.length; inicio += tamanhoLoteLivros) {
            await carregarLoteLivros(idsLivros.slice(inicio, inicio + tamanhoLoteLivros));
        }
    }

    carregarDetalhesLivros();
});

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.conteudo-alternar-detalhes').forEach(function (botao) {
        botao.addEventListener('click', function () {
            const detalhesId = botao.getAttribute('aria-controls');
            const detalhes = detalhesId ? document.getElementById(detalhesId) : null;
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
        .filter(function (item) { return item.dataset.videoId; });
    const itensPorVideoId = new Map();

    itensVideo.forEach(function (item) {
        const itens = itensPorVideoId.get(item.dataset.videoId) || [];
        itens.push(item);
        itensPorVideoId.set(item.dataset.videoId, itens);
    });

    const idsVideos = Array.from(itensPorVideoId.keys());
    const tamanhoLote = 50;

    async function carregarDetalhesVideos(ids) {
        const parametros = new URLSearchParams({ ids: ids.join(',') });
        const resposta = await fetch('/youtube/detalhes?' + parametros.toString(), {
            headers: { Accept: 'application/json' }
        });
        if (!resposta.ok) {
            throw new Error('Falha ao carregar os detalhes dos vídeos.');
        }

        const videos = await resposta.json();
        const videosPorId = new Map(videos.map(function (video) {
            return [video.videoId, video];
        }));

        ids.forEach(function (videoId) {
            const video = videosPorId.get(videoId);
            const itens = itensPorVideoId.get(videoId) || [];

            itens.forEach(function (item) {
                const titulo = item.querySelector('.conteudo-midia-titulo');
                const detalhes = item.querySelector('.conteudo-midia-detalhes');
                if (!titulo || !detalhes) return;

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
        carregarDetalhesVideos(lote).catch(function (erro) {
            console.error('Não foi possível carregar os detalhes dos vídeos da turma.', erro);
            lote.forEach(function (videoId) {
                (itensPorVideoId.get(videoId) || []).forEach(function (item) {
                    const titulo = item.querySelector('.conteudo-midia-titulo');
                    if (titulo) titulo.textContent = 'Informações do vídeo indisponíveis.';
                });
            });
        });
    }
});

document.addEventListener('DOMContentLoaded', function () {
    const buscaParticipantes = document.getElementById('buscaParticipantesTurma');

    if (buscaParticipantes) {
        const linhasParticipantes = Array.from(
            document.querySelectorAll('#listaParticipantesTurma tbody tr')
        );
        const mensagemSemResultados = document.querySelector(
            '#participantesTurma .participantes-sem-resultados'
        );

        function normalizarBuscaParticipante(texto) {
            return texto
                .normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '')
                .toLocaleLowerCase('pt-BR');
        }

        buscaParticipantes.addEventListener('input', function () {
            const consulta = normalizarBuscaParticipante(buscaParticipantes.value.trim());
            let participantesVisiveis = 0;

            linhasParticipantes.forEach(function (linha) {
                const dadosParticipante = Array.from(linha.cells)
                    .slice(0, 3)
                    .map(function (celula) { return celula.textContent; })
                    .join(' ');
                const corresponde = normalizarBuscaParticipante(dadosParticipante).includes(consulta);

                linha.hidden = !corresponde;
                if (corresponde) participantesVisiveis++;
            });

            if (mensagemSemResultados) {
                mensagemSemResultados.hidden = participantesVisiveis > 0;
            }
        });
    }

    document.querySelectorAll('.busca-conteudos').forEach(function (form) {
        const input = form.querySelector('input[name="q"]');
        const filtroTipo = form.querySelector('select[name="tipo"]');
        let temporizadorBusca;

        function enviarBusca() {
            window.clearTimeout(temporizadorBusca);
            form.requestSubmit();
        }

        if (input) {
            input.addEventListener('input', function () {
                window.clearTimeout(temporizadorBusca);
                temporizadorBusca = window.setTimeout(enviarBusca, 600);
            });
        }

        if (filtroTipo) filtroTipo.addEventListener('change', enviarBusca);
    });

    const selectModal = document.getElementById('selecionarConteudoModal');
    let modaisAbrindo = 0;

    document.addEventListener('show.bs.modal', function () {
        modaisAbrindo++;
    });

    document.addEventListener('shown.bs.modal', function () {
        modaisAbrindo = Math.max(0, modaisAbrindo - 1);
    });

    document.addEventListener('hidden.bs.modal', function () {
        setTimeout(function () {
            if (modaisAbrindo > 0 || document.querySelector('.modal.show')) {
                return;
            }

            document.querySelectorAll('.modal-backdrop').forEach(function (backdrop) {
                backdrop.remove();
            });
            document.body.classList.remove('modal-open');
            document.body.style.removeProperty('overflow');
            document.body.style.removeProperty('padding-right');
        }, 50);
    });

    document.querySelectorAll('.conteudo-selecionar-item').forEach(function (button) {
        button.addEventListener('click', function () {
            const conteudoId = this.dataset.id;
            const editModal = document.getElementById('editarConteudoModal-' + conteudoId);

            if (selectModal && editModal) {
                const selectModalInstance = bootstrap.Modal.getOrCreateInstance(selectModal);
                const editModalInstance = bootstrap.Modal.getOrCreateInstance(editModal);

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

    if (participantesModal && adicionarPessoasModal && abrirAdicionarPessoas) {
        abrirAdicionarPessoas.addEventListener('click', function () {
            retornarAosParticipantes = true;
            participantesModal.addEventListener('hidden.bs.modal', function abrirFormulario() {
                bootstrap.Modal.getOrCreateInstance(adicionarPessoasModal).show();
            }, { once: true });
            bootstrap.Modal.getOrCreateInstance(participantesModal).hide();
        });

        adicionarPessoasModal.addEventListener('hidden.bs.modal', function () {
            if (!retornarAosParticipantes) return;

            retornarAosParticipantes = false;
            bootstrap.Modal.getOrCreateInstance(participantesModal).show();
        });
    }

    const campoAutocompletePessoa = document.querySelector('.campo-autocomplete-pessoa');
    const inputPessoa = document.getElementById('emailPessoaAdicionar');
    const listaSugestoesPessoas = document.getElementById('sugestoesPessoasAdicionar');

    if (campoAutocompletePessoa && inputPessoa && listaSugestoesPessoas) {
        const urlSugestoes = campoAutocompletePessoa.dataset.sugestoesUrl;
        let temporizadorSugestoes;
        let requisicaoSugestoes;
        let sequenciaSugestoes = 0;

        function fecharSugestoesPessoas() {
            window.clearTimeout(temporizadorSugestoes);
            temporizadorSugestoes = undefined;
            sequenciaSugestoes++;
            if (requisicaoSugestoes) {
                requisicaoSugestoes.abort();
                requisicaoSugestoes = null;
            }
            listaSugestoesPessoas.replaceChildren();
            listaSugestoesPessoas.hidden = true;
            inputPessoa.setAttribute('aria-expanded', 'false');
            inputPessoa.removeAttribute('aria-activedescendant');
        }

        function mostrarSugestoesPessoas(pessoas) {
            listaSugestoesPessoas.replaceChildren();

            if (!pessoas.length) {
                const vazio = document.createElement('span');
                vazio.className = 'sugestao-pessoa-vazia';
                vazio.textContent = 'Nenhuma pessoa encontrada';
                listaSugestoesPessoas.appendChild(vazio);
            } else {
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

        inputPessoa.addEventListener('input', function () {
            window.clearTimeout(temporizadorSugestoes);
            if (requisicaoSugestoes) requisicaoSugestoes.abort();
            requisicaoSugestoes = null;
            const sequenciaAtual = ++sequenciaSugestoes;

            const consulta = inputPessoa.value.trim();
            if (consulta.length < 2) {
                fecharSugestoesPessoas();
                return;
            }

            temporizadorSugestoes = window.setTimeout(async function () {
                const controller = new AbortController();
                requisicaoSugestoes = controller;
                try {
                    const resposta = await fetch(`${urlSugestoes}?q=${encodeURIComponent(consulta)}`, {
                        headers: { Accept: 'application/json' },
                        signal: controller.signal
                    });
                    if (!resposta.ok) {
                        if (sequenciaAtual === sequenciaSugestoes) fecharSugestoesPessoas();
                        return;
                    }
                    const pessoas = await resposta.json();
                    if (sequenciaAtual === sequenciaSugestoes && !controller.signal.aborted) {
                        mostrarSugestoesPessoas(pessoas);
                    }
                } catch (erro) {
                    if (erro.name !== 'AbortError' && sequenciaAtual === sequenciaSugestoes) {
                        fecharSugestoesPessoas();
                    }
                }
            }, 180);
        });

        inputPessoa.addEventListener('keydown', function (evento) {
            if (evento.key === 'Escape') {
                fecharSugestoesPessoas();
            } else if (evento.key === 'ArrowDown' && !listaSugestoesPessoas.hidden) {
                evento.preventDefault();
                const primeiraOpcao = listaSugestoesPessoas.querySelector('.sugestao-pessoa');
                if (primeiraOpcao) primeiraOpcao.focus();
            }
        });

        document.addEventListener('click', function (evento) {
            if (!campoAutocompletePessoa.contains(evento.target)) fecharSugestoesPessoas();
        });
    }

    const paginaTurma = document.querySelector('.turmas-page');

    if (paginaTurma && paginaTurma.dataset.abrirModalParticipantes === 'true') {
        const modalElement = document.getElementById('participantesTurma');

        if (modalElement) {
            const modal = new bootstrap.Modal(modalElement);
            modal.show();
        }
    }

    function recarregarTurmaAoFechar(modalElement) {
        modalElement.addEventListener('hidden.bs.modal', function () {
            const partes = window.location.pathname.match(/\/turmas\/(\d+)/);
            if (partes) {
                window.location.replace('/turmas/' + partes[1]);
            }
        }, { once: true });
    }

    if (paginaTurma && paginaTurma.dataset.abrirModalConteudo === 'true') {
        const modalElement = document.getElementById('adicionarConteudoModal');
        if (modalElement) {
            recarregarTurmaAoFechar(modalElement);
            bootstrap.Modal.getOrCreateInstance(modalElement).show();
        }
    }

    if (paginaTurma && paginaTurma.dataset.abrirModalEditarTurma === 'true') {
        const modalElement = document.getElementById('editarTurmaModal');
        if (modalElement) {
            bootstrap.Modal.getOrCreateInstance(modalElement).show();
        }
    }

    if (paginaTurma && paginaTurma.dataset.abrirModalEditarId) {
        const modalEditar = document.getElementById('editarConteudoModal-' + paginaTurma.dataset.abrirModalEditarId);
        if (modalEditar) {
            recarregarTurmaAoFechar(modalEditar);
            bootstrap.Modal.getOrCreateInstance(modalEditar).show();
        }
    }

    const formExcluirTurma = document.querySelector('.form-excluir-turma');

    if (formExcluirTurma) {
        formExcluirTurma.addEventListener('submit', function (event) {
            const confirmar = confirm(
                'Tem certeza que deseja excluir esta turma? Isso removerá também todos os alunos e professores vinculados a ela.'
            );

            if (!confirmar) {
                event.preventDefault();
            }
        });
    }

    document.querySelectorAll('.form-remover-participante').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const nome = form.dataset.nome || 'este participante';
            const confirmar = confirm(
                `Tem certeza que deseja remover ${nome} desta turma? Esta ação não pode ser desfeita.`
            );

            if (!confirmar) {
                event.preventDefault();
            }
        });
    });

    document.querySelectorAll('.conteudo-excluir-form').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const confirmar = confirm('Excluir este conteúdo? Essa ação não pode ser desfeita.');

            if (!confirmar) {
                event.preventDefault();
            }
        });
    });
});

// Integrações recuperadas do commit e36e59d (APIsValidation).
function escaparTextoResultado(valor) {
    const texto = document.createElement('span');
    texto.textContent = valor == null ? '' : String(valor);
    return texto.innerHTML.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function mostrarPreviaConteudo(container, titulo, detalhes, thumbnailUrl, tipo) {
    const previa = document.createElement('div');
    previa.className = 'conteudo-selecionado-preview';
    previa.setAttribute('role', 'status');

    if (thumbnailUrl) {
        const thumbnail = document.createElement('img');
        thumbnail.className = 'conteudo-selecionado-thumbnail' +
            (tipo === 'livro' ? ' conteudo-selecionado-thumbnail-livro' : '');
        thumbnail.src = thumbnailUrl;
        thumbnail.alt = '';
        previa.appendChild(thumbnail);
    } else if (tipo === 'livro') {
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

async function lerRespostaBuscaLivros(resposta) {
    let corpo;
    try {
        corpo = await resposta.json();
    } catch (erro) {
        if (!resposta.ok) {
            throw new Error('Não foi possível consultar o Google Books. Tente novamente.');
        }
        throw erro;
    }

    if (!resposta.ok) {
        throw new Error(corpo.message || 'Não foi possível consultar o Google Books. Tente novamente.');
    }

    return corpo;
}

document.addEventListener('DOMContentLoaded', function () {

    function ligarContador(textarea, contador) {
        if (!textarea || !contador) return;

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

    document.querySelectorAll('textarea[name="descricao"]').forEach(function (textarea) {
        const modal = textarea.closest('.modal');
        if (!modal) return;
        const contador = modal.querySelector('[id^="contadorDescricaoEditar-"]');
        ligarContador(textarea, contador);
    });
});

document.addEventListener('DOMContentLoaded', function () {

    const selectTipo = document.getElementById('tipoConteudo');
    const campoLink = document.getElementById('campoLink');
    const campoYoutube = document.getElementById('campoYoutube');
    const campoGoogleBooks = document.getElementById('campoGoogleBooks');
    const campoArquivo = document.getElementById('campoArquivo');

    function atualizarCamposVisiveis() {
        if (!selectTipo) return;

        const tipo = selectTipo.value;

        campoLink.style.display = (tipo === 'LINK') ? '' : 'none';
        campoYoutube.style.display = (tipo === 'YOUTUBE') ? '' : 'none';
        campoGoogleBooks.style.display = (tipo === 'GOOGLE_BOOKS') ? '' : 'none';
        campoArquivo.style.display = (tipo === 'ARQUIVO') ? '' : 'none';
    }

    if (selectTipo) {
        selectTipo.value = 'LINK';
        selectTipo.addEventListener('change', atualizarCamposVisiveis);
        atualizarCamposVisiveis();
    }

    // Busca de vídeos do YouTube
    const inputBuscaYoutube = document.getElementById('buscaYoutube');
    const resultadosYoutube = document.getElementById('resultadosYoutube');
    const inputUrl = document.getElementById('url');
    const inputTitulo = document.getElementById('titulo');
    const inputDuracao = document.getElementById('duracaoEstimada');
    let nextPageTokenYoutube = null;
    let termoYoutubeAtual = '';
    let sequenciaBuscaYoutube = 0;
    let carregandoVideos = false;

    function buscarVideos(carregarMais) {
        const termo = inputBuscaYoutube.value.trim();
        if (!termo) return;

        if (carregarMais) {
            if (!nextPageTokenYoutube || carregandoVideos || termo !== termoYoutubeAtual) return;
        } else {
            termoYoutubeAtual = termo;
            nextPageTokenYoutube = null;
            sequenciaBuscaYoutube++;
        }

        const sequenciaAtual = sequenciaBuscaYoutube;
        carregandoVideos = true;
        const botaoCarregarMais = resultadosYoutube.querySelector('.carregar-mais-resultados');
        if (carregarMais && botaoCarregarMais) {
            botaoCarregarMais.disabled = true;
            botaoCarregarMais.textContent = 'Carregando...';
        } else {
            resultadosYoutube.innerHTML = '<p class="form-text">Buscando...</p>';
        }

        const parametros = new URLSearchParams({ q: termo });
        if (carregarMais) parametros.set('pageToken', nextPageTokenYoutube);

        fetch('/youtube/buscar?' + parametros.toString())
            .then(function (resposta) {
                if (!resposta.ok) throw new Error('Falha na busca');
                return resposta.json().then(function (videos) {
                    return {
                        videos: videos,
                        nextPageToken: resposta.headers.get('X-Next-Page-Token')
                    };
                });
            })
            .then(function (pagina) {
                if (sequenciaAtual !== sequenciaBuscaYoutube ||
                    inputBuscaYoutube.value.trim() !== termo) return;
                if (!carregarMais) resultadosYoutube.innerHTML = '';
                else if (botaoCarregarMais) botaoCarregarMais.remove();

                if (!pagina.videos.length && !carregarMais) {
                    resultadosYoutube.innerHTML = '<p class="form-text">Nenhum vídeo encontrado.</p>';
                    return;
                }

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

                    item.addEventListener('click', function () {
                        inputUrl.value = 'https://www.youtube.com/watch?v=' + video.videoId;
                        if (inputTitulo && !inputTitulo.value.trim()) {
                            inputTitulo.value = video.titulo;
                        }
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
                if (nextPageTokenYoutube) {
                    const botao = document.createElement('button');
                    botao.type = 'button';
                    botao.className = 'carregar-mais-resultados';
                    botao.textContent = 'Carregar mais vídeos';
                    botao.addEventListener('click', function () { buscarVideos(true); });
                    resultadosYoutube.appendChild(botao);
                }
            })
            .catch(function () {
                if (sequenciaAtual === sequenciaBuscaYoutube &&
                    inputBuscaYoutube.value.trim() === termo) {
                    if (carregarMais && botaoCarregarMais) {
                        botaoCarregarMais.disabled = false;
                        botaoCarregarMais.textContent = 'Tentar carregar mais vídeos';
                    } else {
                        resultadosYoutube.innerHTML = '<p class="erro-campo">Erro ao buscar vídeos. Tente novamente.</p>';
                    }
                }
            })
            .finally(function () {
                if (sequenciaAtual === sequenciaBuscaYoutube) carregandoVideos = false;
            });
    }

    configurarBuscaAutomatica(inputBuscaYoutube, buscarVideos, resultadosYoutube);

    if (inputBuscaYoutube) {
        inputBuscaYoutube.addEventListener('keydown', function (evento) {
            if (evento.key === 'Enter') {
                evento.preventDefault();
                buscarVideos(false);
            }
        });
    }
});

document.addEventListener('DOMContentLoaded', function () {

    document.querySelectorAll('.tipo-conteudo-editar').forEach(function (selectTipo) {
        const modalBody = selectTipo.closest('.modal-body');
        if (!modalBody) return;

        const campoLinkEditar = modalBody.querySelector('.campo-link-editar');
        const campoYoutubeEditar = modalBody.querySelector('.campo-youtube-editar');
        const campoGoogleBooks = modalBody.querySelector('.campo-google-books-editar');
        const campoArquivoEditar = modalBody.querySelector('.campo-arquivo-editar');
        const inputUrlEditar = campoLinkEditar.querySelector('input[name="url"]');
        const inputDuracaoEditar = modalBody.querySelector('input[name="duracaoEstimada"]');

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

        // Busca de vídeos do YouTube (Editar)
        const inputBuscaYoutubeEditar = campoYoutubeEditar.querySelector('.busca-youtube-editar');
        const resultadosYoutubeEditar = campoYoutubeEditar.querySelector('.resultados-youtube-editar');

        function buscarVideosEditar() {
            const termo = inputBuscaYoutubeEditar.value.trim();
            if (!termo) return;

            resultadosYoutubeEditar.innerHTML = '<p class="form-text">Buscando...</p>';

            fetch('/youtube/buscar?q=' + encodeURIComponent(termo))
                .then(function (resposta) {
                    if (!resposta.ok) throw new Error('Falha na busca');
                    return resposta.json();
                })
                .then(function (videos) {
                    if (inputBuscaYoutubeEditar.value.trim() !== termo) return;
                    resultadosYoutubeEditar.innerHTML = '';

                    if (videos.length === 0) {
                        resultadosYoutubeEditar.innerHTML = '<p class="form-text">Nenhum vídeo encontrado.</p>';
                        return;
                    }

                    videos.forEach(function (video) {
                        const item = document.createElement('div');
                        item.style.cssText = 'display:flex; gap:10px; align-items:center; padding:8px; border:1px solid #ddd; border-radius:8px; margin-bottom:6px; cursor:pointer;';

                        item.innerHTML =
                            '<img src="' + escaparTextoResultado(video.thumbnailUrl) + '" style="width:80px; border-radius:4px;">' +
                            '<div>' +
                            '<div style="font-weight:600; font-size:14px;">' + escaparTextoResultado(video.titulo) + '</div>' +
                            '<div style="font-size:12px; color:#666;">' + escaparTextoResultado(video.canal) + ' · ' + escaparTextoResultado(video.duracao) + '</div>' +
                            '</div>';

                        item.addEventListener('click', function () {
                            inputUrlEditar.value = 'https://www.youtube.com/watch?v=' + video.videoId;
                            if (inputDuracaoEditar) inputDuracaoEditar.value = video.duracao;
                            resultadosYoutubeEditar.innerHTML = '<p class="form-text">Selecionado: ' + escaparTextoResultado(video.titulo) + '</p>';
                        });

                        resultadosYoutubeEditar.appendChild(item);
                    });
                })
                .catch(function () {
                    if (inputBuscaYoutubeEditar.value.trim() === termo) {
                        resultadosYoutubeEditar.innerHTML = '<p class="erro-campo">Erro ao buscar vídeos. Tente novamente.</p>';
                    }
                });
        }

        configurarBuscaAutomatica(inputBuscaYoutubeEditar, buscarVideosEditar, resultadosYoutubeEditar);

        if (inputBuscaYoutubeEditar) {
            inputBuscaYoutubeEditar.addEventListener('keydown', function (evento) {
                if (evento.key === 'Enter') {
                    evento.preventDefault();
                    buscarVideosEditar();
                }
            });
        }

        // Busca de livros do Google Books (Editar)
        const inputBuscaBooksEditar = campoGoogleBooks.querySelector('.busca-google-books-editar');
        const resultadosBooksEditar = campoGoogleBooks.querySelector('.resultados-google-books-editar');

        function buscarLivrosEditar() {
            const termo = inputBuscaBooksEditar.value.trim();
            if (!termo) return;

            resultadosBooksEditar.innerHTML = '<p class="form-text">Buscando...</p>';

            fetch('/googlebooks/buscar?q=' + encodeURIComponent(termo))
                .then(lerRespostaBuscaLivros)
                .then(function (livros) {
                    if (inputBuscaBooksEditar.value.trim() !== termo) return;
                    resultadosBooksEditar.innerHTML = '';

                    if (livros.length === 0) {
                        resultadosBooksEditar.innerHTML = '<p class="form-text">Nenhum livro encontrado.</p>';
                        return;
                    }

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

                        item.addEventListener('click', function () {
                            inputUrlEditar.value = livro.linkGoogleBooks;
                            resultadosBooksEditar.innerHTML = '<p class="form-text">Selecionado: ' + escaparTextoResultado(livro.titulo) + '</p>';
                        });

                        resultadosBooksEditar.appendChild(item);
                    });
                })
                .catch(function (erro) {
                    if (inputBuscaBooksEditar.value.trim() === termo) {
                        resultadosBooksEditar.innerHTML =
                            '<p class="erro-campo">' +
                            escaparTextoResultado(erro.message || 'Erro ao buscar livros. Tente novamente.') +
                            '</p>';
                    }
                });
        }

        configurarBuscaAutomatica(inputBuscaBooksEditar, buscarLivrosEditar, resultadosBooksEditar);

        if (inputBuscaBooksEditar) {
            inputBuscaBooksEditar.addEventListener('keydown', function (evento) {
                if (evento.key === 'Enter') {
                    evento.preventDefault();
                    buscarLivrosEditar();
                }
            });
        }
    });

    // Busca de livros do Google Books (modal Adicionar Conteúdo)
    const inputBuscaBooks = document.getElementById('buscaGoogleBooks');
    const resultadosBooks = document.getElementById('resultadosGoogleBooks');
    const inputUrlAdicionar = document.getElementById('url');
    const inputTituloAdicionar = document.getElementById('titulo');
    let nextStartIndexBooks = null;
    let termoBooksAtual = '';
    let sequenciaBuscaBooks = 0;
    let carregandoLivros = false;

    function buscarLivros(carregarMais) {
        const termo = inputBuscaBooks.value.trim();
        if (!termo) return;

        if (carregarMais) {
            if (nextStartIndexBooks === null || carregandoLivros || termo !== termoBooksAtual) return;
        } else {
            termoBooksAtual = termo;
            nextStartIndexBooks = null;
            sequenciaBuscaBooks++;
        }

        const sequenciaAtual = sequenciaBuscaBooks;
        carregandoLivros = true;
        const botaoCarregarMais = resultadosBooks.querySelector('.carregar-mais-resultados');
        if (carregarMais && botaoCarregarMais) {
            botaoCarregarMais.disabled = true;
            botaoCarregarMais.textContent = 'Carregando...';
        } else {
            resultadosBooks.innerHTML = '<p class="form-text">Buscando...</p>';
        }

        const parametros = new URLSearchParams({ q: termo });
        if (carregarMais) parametros.set('startIndex', nextStartIndexBooks);

        fetch('/googlebooks/buscar?' + parametros.toString())
            .then(function (resposta) {
                return lerRespostaBuscaLivros(resposta).then(function (livros) {
                    return {
                        livros: livros,
                        nextStartIndex: resposta.headers.get('X-Next-Start-Index')
                    };
                });
            })
            .then(function (pagina) {
                if (sequenciaAtual !== sequenciaBuscaBooks ||
                    inputBuscaBooks.value.trim() !== termo) return;
                if (!carregarMais) resultadosBooks.innerHTML = '';
                else if (botaoCarregarMais) botaoCarregarMais.remove();

                if (!pagina.livros.length && !carregarMais) {
                    resultadosBooks.innerHTML = '<p class="form-text">Nenhum livro encontrado.</p>';
                    return;
                }

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

                    item.addEventListener('click', function () {
                        inputUrlAdicionar.value = livro.linkGoogleBooks;
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
                if (nextStartIndexBooks !== null && Number.isFinite(nextStartIndexBooks)) {
                    const botao = document.createElement('button');
                    botao.type = 'button';
                    botao.className = 'carregar-mais-resultados';
                    botao.textContent = 'Carregar mais livros';
                    botao.addEventListener('click', function () { buscarLivros(true); });
                    resultadosBooks.appendChild(botao);
                }
            })
            .catch(function (erro) {
                if (sequenciaAtual === sequenciaBuscaBooks &&
                    inputBuscaBooks.value.trim() === termo) {
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
            .finally(function () {
                if (sequenciaAtual === sequenciaBuscaBooks) carregandoLivros = false;
            });
    }

    configurarBuscaAutomatica(inputBuscaBooks, buscarLivros, resultadosBooks);

    if (inputBuscaBooks) {
        inputBuscaBooks.addEventListener('keydown', function (evento) {
            if (evento.key === 'Enter') {
                evento.preventDefault();
                buscarLivros(false);
            }
        });
    }
});
