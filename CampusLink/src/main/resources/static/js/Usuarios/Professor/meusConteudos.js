document.addEventListener('DOMContentLoaded', function () {
    const campoBusca = document.getElementById('campoBusca');
    const filtroStatus = document.getElementById('filtroStatus');
    const blocoConteudos = document.getElementById('blocoConteudos');
    const listaConteudos = document.getElementById('listaConteudos');
    const botaoMostrarMais = document.getElementById('botaoMostrarMais');
    const totalExibido = document.getElementById('totalExibido');
    const totalEncontrado = document.getElementById('totalEncontrado');
    const erroCarregamento = document.getElementById('erroCarregamento');

    let buscaAplicada = campoBusca.value.trim();
    let statusAplicado = filtroStatus.value;
    let numeroRequisicao = 0;
    let tempoBusca;
    let carregando = false;
    let filtrosPendentes = false;

    async function carregarDetalhesMidia(container) {
        const itensLivro = Array.from(container.querySelectorAll('.conteudo-midia[data-book-id]'))
            .filter(function (item) { return item.dataset.bookId && item.dataset.mediaCarregada !== 'true'; });
        const itensVideo = Array.from(container.querySelectorAll('.conteudo-midia[data-video-id]'))
            .filter(function (item) { return item.dataset.videoId && item.dataset.mediaCarregada !== 'true'; });

        itensLivro.forEach(function (item) { item.dataset.mediaCarregada = 'true'; });
        itensVideo.forEach(function (item) { item.dataset.mediaCarregada = 'true'; });

        const itensPorLivro = new Map();
        itensLivro.forEach(function (item) {
            const itens = itensPorLivro.get(item.dataset.bookId) || [];
            itens.push(item);
            itensPorLivro.set(item.dataset.bookId, itens);
        });

        const idsLivros = Array.from(itensPorLivro.keys());
        for (let inicio = 0; inicio < idsLivros.length; inicio += 5) {
            const lote = idsLivros.slice(inicio, inicio + 5);
            const resultados = await Promise.allSettled(lote.map(async function (id) {
                const parametros = new URLSearchParams({ id: id });
                const resposta = await fetch('/googlebooks/detalhes?' + parametros.toString(), {
                    headers: { Accept: 'application/json' }
                });
                if (!resposta.ok) {
                    throw new Error('Falha ao carregar as informações do livro (' + resposta.status + ').');
                }
                return resposta.json();
            }));

            resultados.forEach(function (resultado, indice) {
                const id = lote[indice];
                const itens = itensPorLivro.get(id) || [];
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
                        autor.textContent = [livro.autores, livro.anoPublicacao].filter(Boolean).join(' · ');
                    }
                    if (link && livro.titulo) link.setAttribute('aria-label', 'Abrir livro: ' + livro.titulo);
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

        const idsVideos = Array.from(new Set(itensVideo.map(function (item) { return item.dataset.videoId; })));
        for (let inicio = 0; inicio < idsVideos.length; inicio += 50) {
            const lote = idsVideos.slice(inicio, inicio + 50);
            try {
                const parametros = new URLSearchParams({ ids: lote.join(',') });
                const resposta = await fetch('/youtube/detalhes?' + parametros.toString(), {
                    headers: { Accept: 'application/json' }
                });
                if (!resposta.ok) {
                    throw new Error('Falha ao carregar as informações dos vídeos (' + resposta.status + ').');
                }
                const videos = await resposta.json();
                const videosPorId = new Map(videos.map(function (video) { return [video.videoId, video]; }));

                lote.forEach(function (id) {
                    const video = videosPorId.get(id);
                    itensVideo.filter(function (item) { return item.dataset.videoId === id; })
                        .forEach(function (item) {
                            const titulo = item.querySelector('.conteudo-midia-titulo');
                            const detalhes = item.querySelector('.conteudo-midia-detalhes');
                            if (!video) {
                                if (titulo) titulo.textContent = 'Informações do vídeo indisponíveis.';
                                return;
                            }
                            if (titulo) {
                                titulo.textContent = video.titulo || 'Vídeo do YouTube';
                                titulo.title = video.titulo || '';
                            }
                            if (detalhes) detalhes.textContent = [video.canal, video.duracao].filter(Boolean).join(' · ');
                        });
                });
            } catch (erro) {
                console.error('Não foi possível carregar os detalhes dos vídeos.', erro);
                itensVideo.filter(function (item) { return lote.includes(item.dataset.videoId); })
                    .forEach(function (item) {
                        const titulo = item.querySelector('.conteudo-midia-titulo');
                        if (titulo) titulo.textContent = 'Informações do vídeo indisponíveis.';
                    });
            }
        }
    }

    function atualizarBotao() {
        botaoMostrarMais.disabled = carregando;
        botaoMostrarMais.classList.toggle('d-none', blocoConteudos.dataset.temMais !== 'true' && !filtrosPendentes);
        listaConteudos.setAttribute('aria-busy', String(carregando));

        if (carregando) {
            botaoMostrarMais.textContent = 'Carregando...';
        } else if (filtrosPendentes) {
            botaoMostrarMais.textContent = 'Tentar novamente';
        } else {
            botaoMostrarMais.textContent = 'Mostrar mais 10';
        }
    }

    async function carregarConteudos(pagina, adicionar) {
        const requisicaoAtual = ++numeroRequisicao;
        const busca = adicionar ? buscaAplicada : campoBusca.value.trim();
        const status = adicionar ? statusAplicado : filtroStatus.value;
        const url = new URL(blocoConteudos.dataset.url, window.location.origin);

        url.searchParams.set('pagina', pagina);
        url.searchParams.set('busca', busca);
        url.searchParams.set('status', status);
        url.searchParams.set('fragmento', 'true');

        carregando = true;
        erroCarregamento.classList.add('d-none');
        atualizarBotao();

        try {
            const resposta = await fetch(url);

            if (requisicaoAtual !== numeroRequisicao) {
                return;
            }

            if (resposta.redirected) {
                window.location.href = resposta.url;
                return;
            }

            if (!resposta.ok) {
                throw new Error('Não foi possível carregar os conteúdos');
            }

            const html = await resposta.text();

            // ignora uma resposta antiga quando o filtro ja mudou
            if (requisicaoAtual !== numeroRequisicao) {
                return;
            }

            const documento = new DOMParser().parseFromString(html, 'text/html');
            const novoBloco = documento.getElementById('blocoConteudos');
            const novaLista = documento.getElementById('listaConteudos');

            if (!novoBloco || !novaLista) {
                throw new Error('A lista de conteúdos não foi encontrada');
            }

            if (adicionar) {
                novaLista.querySelectorAll('.conteudo-card').forEach(function (conteudo) {
                    listaConteudos.appendChild(conteudo);
                });
            } else {
                listaConteudos.innerHTML = novaLista.innerHTML;
            }
            carregarDetalhesMidia(listaConteudos);

            blocoConteudos.dataset.proximaPagina = novoBloco.dataset.proximaPagina;
            blocoConteudos.dataset.temMais = novoBloco.dataset.temMais;
            blocoConteudos.dataset.total = novoBloco.dataset.total;

            buscaAplicada = busca;
            statusAplicado = status;
            filtrosPendentes = false;

            totalExibido.textContent = listaConteudos.querySelectorAll('.conteudo-card').length;
            totalEncontrado.textContent = novoBloco.dataset.total;

        } catch (erro) {
            if (requisicaoAtual === numeroRequisicao) {
                erroCarregamento.textContent = 'Não foi possível carregar os conteúdos. Clique no botão abaixo para tentar novamente.';
                erroCarregamento.classList.remove('d-none');
            }
        } finally {
            if (requisicaoAtual === numeroRequisicao) {
                carregando = false;
                atualizarBotao();
            }
        }
    }

    function prepararFiltros() {
        clearTimeout(tempoBusca);
        numeroRequisicao++;
        filtrosPendentes = true;
        carregando = true;
        erroCarregamento.classList.add('d-none');
        atualizarBotao();
    }

    campoBusca.addEventListener('input', function () {
        prepararFiltros();
        tempoBusca = setTimeout(function () {
            carregarConteudos(0, false);
        }, 350);
    });

    filtroStatus.addEventListener('change', function () {
        prepararFiltros();
        carregarConteudos(0, false);
    });

    botaoMostrarMais.addEventListener('click', function () {
        if (carregando) {
            return;
        }

        if (filtrosPendentes) {
            carregarConteudos(0, false);
        } else if (blocoConteudos.dataset.temMais === 'true') {
            carregarConteudos(Number(blocoConteudos.dataset.proximaPagina), true);
        }
    });

    // mantem a confirmacao nos conteudos carregados pelo botao
    listaConteudos.addEventListener('submit', function (event) {
        if (event.target.matches('.form-remover-conteudo')) {
            if (!confirm('Tem certeza que deseja remover este conteúdo?')) {
                event.preventDefault();
            }
        }
    });

    listaConteudos.addEventListener('click', function (event) {
        if (!(event.target instanceof Element)) return;
        const botao = event.target.closest('.conteudo-alternar-detalhes');
        if (!botao || !listaConteudos.contains(botao)) return;

        const detalhesId = botao.getAttribute('aria-controls');
        const detalhes = detalhesId ? document.getElementById(detalhesId) : null;
        if (!detalhes) return;

        const novoEstado = botao.getAttribute('aria-expanded') !== 'true';
        detalhes.hidden = !novoEstado;
        botao.setAttribute('aria-expanded', String(novoEstado));
        const cartao = botao.closest('.conteudo-card');
        const tituloElemento = cartao ? cartao.querySelector('.conteudo-titulo h2') : null;
        const titulo = tituloElemento ? tituloElemento.textContent.trim() : '';
        const acao = novoEstado ? 'Recolher' : 'Expandir';
        const rotulo = titulo ? acao + ' detalhes de ' + titulo : acao + ' detalhes do conteúdo';
        botao.setAttribute('aria-label', rotulo);
        botao.title = rotulo;
    });

    carregarDetalhesMidia(listaConteudos);
    atualizarBotao();
});