document.addEventListener('DOMContentLoaded', function () {
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

        function fecharSugestoesPessoas() {
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

            const consulta = inputPessoa.value.trim();
            if (consulta.length < 2) {
                fecharSugestoesPessoas();
                return;
            }

            temporizadorSugestoes = window.setTimeout(async function () {
                requisicaoSugestoes = new AbortController();
                try {
                    const resposta = await fetch(`${urlSugestoes}?q=${encodeURIComponent(consulta)}`, {
                        headers: { Accept: 'application/json' },
                        signal: requisicaoSugestoes.signal
                    });
                    if (!resposta.ok) {
                        fecharSugestoesPessoas();
                        return;
                    }
                    mostrarSugestoesPessoas(await resposta.json());
                } catch (erro) {
                    if (erro.name !== 'AbortError') fecharSugestoesPessoas();
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
    const campoDuracao = document.getElementById('campoDuracao');

    function atualizarCamposVisiveis() {
        if (!selectTipo) return;

        const tipo = selectTipo.value;

        campoLink.style.display = (tipo === 'LINK') ? '' : 'none';
        campoYoutube.style.display = (tipo === 'YOUTUBE') ? '' : 'none';
        campoGoogleBooks.style.display = (tipo === 'GOOGLE_BOOKS') ? '' : 'none';
        campoArquivo.style.display = (tipo === 'ARQUIVO') ? '' : 'none';
        campoDuracao.style.display = (tipo === 'YOUTUBE') ? '' : 'none';
    }

    if (selectTipo) {
        selectTipo.value = 'LINK';
        selectTipo.addEventListener('change', atualizarCamposVisiveis);
        atualizarCamposVisiveis();
    }

    // Busca de vídeos do YouTube
    const btnBuscarYoutube = document.getElementById('btnBuscarYoutube');
    const inputBuscaYoutube = document.getElementById('buscaYoutube');
    const resultadosYoutube = document.getElementById('resultadosYoutube');
    const inputUrl = document.getElementById('url');
    const inputTitulo = document.getElementById('titulo');
    const inputDuracao = document.getElementById('duracaoEstimada');

    function buscarVideos() {
        const termo = inputBuscaYoutube.value.trim();
        if (!termo) return;

        resultadosYoutube.innerHTML = '<p class="form-text">Buscando...</p>';

        fetch('/youtube/buscar?q=' + encodeURIComponent(termo))
            .then(function (resposta) {
                if (!resposta.ok) throw new Error('Falha na busca');
                return resposta.json();
            })
            .then(function (videos) {
                resultadosYoutube.innerHTML = '';

                if (videos.length === 0) {
                    resultadosYoutube.innerHTML = '<p class="form-text">Nenhum vídeo encontrado.</p>';
                    return;
                }

                videos.forEach(function (video) {
                    const item = document.createElement('div');
                    item.className = 'resultado-youtube-item';
                    item.style.cssText = 'display:flex; gap:10px; align-items:center; padding:8px; border:1px solid #ddd; border-radius:8px; margin-bottom:6px; cursor:pointer;';

                    item.innerHTML =
                        '<img src="' + escaparTextoResultado(video.thumbnailUrl) + '" style="width:80px; border-radius:4px;">' +
                        '<div>' +
                        '<div style="font-weight:600; font-size:14px;">' + escaparTextoResultado(video.titulo) + '</div>' +
                        '<div style="font-size:12px; color:#666;">' + escaparTextoResultado(video.canal) + ' · ' + escaparTextoResultado(video.duracao) + '</div>' +
                        '</div>';

                    item.addEventListener('click', function () {
                        inputUrl.value = 'https://www.youtube.com/watch?v=' + video.videoId;
                        if (inputTitulo && !inputTitulo.value.trim()) {
                            inputTitulo.value = video.titulo;
                        }
                        if (inputDuracao) {
                            inputDuracao.value = video.duracao;
                        }
                        resultadosYoutube.innerHTML = '<p class="form-text">Selecionado: ' + escaparTextoResultado(video.titulo) + '</p>';
                    });

                    resultadosYoutube.appendChild(item);
                });
            })
            .catch(function () {
                resultadosYoutube.innerHTML = '<p class="erro-campo">Erro ao buscar vídeos. Tente novamente.</p>';
            });
    }

    if (btnBuscarYoutube) {
        btnBuscarYoutube.addEventListener('click', buscarVideos);
    }

    if (inputBuscaYoutube) {
        inputBuscaYoutube.addEventListener('keydown', function (evento) {
            if (evento.key === 'Enter') {
                evento.preventDefault();
                buscarVideos();
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
        const campoDuracaoEditar = modalBody.querySelector('.campo-duracao-editar');
        const campoArquivoEditar = modalBody.querySelector('.campo-arquivo-editar');
        const inputUrlEditar = campoLinkEditar.querySelector('input[name="url"]');
        const inputDuracaoEditar = modalBody.querySelector('input[name="duracaoEstimada"]');

        function atualizarCampos() {
            const tipo = selectTipo.value;
            campoLinkEditar.style.display = (tipo === 'LINK') ? '' : 'none';
            campoYoutubeEditar.style.display = (tipo === 'YOUTUBE') ? '' : 'none';
            campoGoogleBooks.style.display = (tipo === 'GOOGLE_BOOKS') ? '' : 'none';
            campoDuracaoEditar.style.display = (tipo === 'YOUTUBE') ? '' : 'none';
            campoArquivoEditar.style.display = (tipo === 'ARQUIVO') ? '' : 'none';
        }

        selectTipo.value = 'LINK';
        selectTipo.addEventListener('change', atualizarCampos);
        atualizarCampos();

        // Busca de vídeos do YouTube (Editar)
        const inputBuscaYoutubeEditar = campoYoutubeEditar.querySelector('.busca-youtube-editar');
        const btnBuscarYoutubeEditar = campoYoutubeEditar.querySelector('.btn-buscar-youtube-editar');
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
                    resultadosYoutubeEditar.innerHTML = '<p class="erro-campo">Erro ao buscar vídeos. Tente novamente.</p>';
                });
        }

        if (btnBuscarYoutubeEditar) btnBuscarYoutubeEditar.addEventListener('click', buscarVideosEditar);
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
        const btnBuscarBooksEditar = campoGoogleBooks.querySelector('.btn-buscar-google-books-editar');
        const resultadosBooksEditar = campoGoogleBooks.querySelector('.resultados-google-books-editar');

        function buscarLivrosEditar() {
            const termo = inputBuscaBooksEditar.value.trim();
            if (!termo) return;

            resultadosBooksEditar.innerHTML = '<p class="form-text">Buscando...</p>';

            fetch('/googlebooks/buscar?q=' + encodeURIComponent(termo))
                .then(function (resposta) {
                    if (!resposta.ok) throw new Error('Falha na busca');
                    return resposta.json();
                })
                .then(function (livros) {
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
                .catch(function () {
                    resultadosBooksEditar.innerHTML = '<p class="erro-campo">Erro ao buscar livros. Tente novamente.</p>';
                });
        }

        if (btnBuscarBooksEditar) btnBuscarBooksEditar.addEventListener('click', buscarLivrosEditar);
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
    const btnBuscarBooks = document.getElementById('btnBuscarGoogleBooks');
    const inputBuscaBooks = document.getElementById('buscaGoogleBooks');
    const resultadosBooks = document.getElementById('resultadosGoogleBooks');
    const inputUrlAdicionar = document.getElementById('url');
    const inputTituloAdicionar = document.getElementById('titulo');

    function buscarLivros() {
        const termo = inputBuscaBooks.value.trim();
        if (!termo) return;

        resultadosBooks.innerHTML = '<p class="form-text">Buscando...</p>';

        fetch('/googlebooks/buscar?q=' + encodeURIComponent(termo))
            .then(function (resposta) {
                if (!resposta.ok) throw new Error('Falha na busca');
                return resposta.json();
            })
            .then(function (livros) {
                resultadosBooks.innerHTML = '';

                if (livros.length === 0) {
                    resultadosBooks.innerHTML = '<p class="form-text">Nenhum livro encontrado.</p>';
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
                        inputUrlAdicionar.value = livro.linkGoogleBooks;
                        if (inputTituloAdicionar && !inputTituloAdicionar.value.trim()) {
                            inputTituloAdicionar.value = livro.titulo;
                        }
                        resultadosBooks.innerHTML = '<p class="form-text">Selecionado: ' + escaparTextoResultado(livro.titulo) + '</p>';
                    });

                    resultadosBooks.appendChild(item);
                });
            })
            .catch(function () {
                resultadosBooks.innerHTML = '<p class="erro-campo">Erro ao buscar livros. Tente novamente.</p>';
            });
    }

    if (btnBuscarBooks) {
        btnBuscarBooks.addEventListener('click', buscarLivros);
    }

    if (inputBuscaBooks) {
        inputBuscaBooks.addEventListener('keydown', function (evento) {
            if (evento.key === 'Enter') {
                evento.preventDefault();
                buscarLivros();
            }
        });
    }
});
