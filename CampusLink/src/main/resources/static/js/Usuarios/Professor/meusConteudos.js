// conteudos do professor reune busca paginacao detalhes de midia e confirmacao de remocao
// preparar meus conteudos configura busca paginacao midias confirmacoes e detalhes recolhiveis
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

    // carregar midias dos conteudos busca livros e videos ainda nao carregados em lotes
    async function carregarDetalhesMidia(container) {
        const itensLivro = Array.from(container.querySelectorAll('.conteudo-midia[data-book-id]'))
            // filtrar livros pendentes seleciona livros identificados que ainda nao tiveram os dados carregados
            .filter(function (item) { return item.dataset.bookId && item.dataset.mediaCarregada !== 'true'; });
        const itensVideo = Array.from(container.querySelectorAll('.conteudo-midia[data-video-id]'))
            // filtrar videos pendentes seleciona videos identificados que ainda nao tiveram os dados carregados
            .filter(function (item) { return item.dataset.videoId && item.dataset.mediaCarregada !== 'true'; });

        // marcar livro processado impede outra consulta para o mesmo cartao
        itensLivro.forEach(function (item) { item.dataset.mediaCarregada = 'true'; });
        // marcar video processado impede outra consulta para o mesmo cartao
        itensVideo.forEach(function (item) { item.dataset.mediaCarregada = 'true'; });

        const itensPorLivro = new Map();
        // reunir cartoes do mesmo livro agrupa os itens pelo codigo do livro
        itensLivro.forEach(function (item) {
            const itens = itensPorLivro.get(item.dataset.bookId) || [];
            itens.push(item);
            itensPorLivro.set(item.dataset.bookId, itens);
        });

        const idsLivros = Array.from(itensPorLivro.keys());
        for (let inicio = 0; inicio < idsLivros.length; inicio += 5) {
            const lote = idsLivros.slice(inicio, inicio + 5);
            // consultar livro do conteudo busca os detalhes pelo codigo e rejeita falhas da resposta
            const resultados = await Promise.allSettled(lote.map(async function (id) {
                const parametros = new URLSearchParams({ id: id });
                const resposta = await fetch('/googlebooks/detalhes?' + parametros.toString(), {
                    headers: { Accept: 'application/json' }
                });
                // conferir detalhes do livro informa o codigo da falha quando a consulta nao tem sucesso
                if (!resposta.ok) {
                    throw new Error('Falha ao carregar as informações do livro (' + resposta.status + ').');
                }
                return resposta.json();
            }));

            // tratar livro consultado distribui os dados ou informa indisponibilidade nos cartoes
            resultados.forEach(function (resultado, indice) {
                const id = lote[indice];
                const itens = itensPorLivro.get(id) || [];
                // tratar livro indisponivel mostra a falha nos itens cuja consulta foi rejeitada
                if (resultado.status === 'rejected') {
                    console.warn('Não foi possível carregar os detalhes do livro.', resultado.reason);
                    // mostrar falha do livro troca o titulo e limpa os autores quando a consulta falha
                    itens.forEach(function (item) {
                        const titulo = item.querySelector('.conteudo-midia-livro-titulo');
                        const autor = item.querySelector('.conteudo-midia-livro-autor');
                        // mostrar titulo indisponivel avisa sobre a falha dos dados do livro
                        if (titulo) titulo.textContent = 'Informações do livro indisponíveis.';
                        // limpar autor do livro apaga o autor quando a consulta falha
                        if (autor) autor.textContent = '';
                    });
                    return;
                }

                const livro = resultado.value;
                // preencher livro do conteudo atualiza titulo autores ano capa e descricao do link
                itens.forEach(function (item) {
                    const titulo = item.querySelector('.conteudo-midia-livro-titulo');
                    const autor = item.querySelector('.conteudo-midia-livro-autor');
                    const imagem = item.querySelector('.conteudo-midia-link img');
                    const link = item.querySelector('.conteudo-midia-link');
                    // preencher titulo do livro usa o titulo encontrado ou o nome padrao
                    if (titulo) {
                        titulo.textContent = livro.titulo || 'Livro do Google Books';
                        titulo.title = livro.titulo || '';
                    }
                    // preencher autores do livro apresenta autores e ano disponiveis
                    if (autor) {
                        autor.textContent = [livro.autores, livro.anoPublicacao].filter(Boolean).join(' · ');
                    }
                    // descrever link do livro inclui o titulo na descricao acessivel
                    if (link && livro.titulo) link.setAttribute('aria-label', 'Abrir livro: ' + livro.titulo);
                    // carregar capa do livro usa a imagem disponivel e prepara uma alternativa em caso de erro
                    if (imagem && livro.thumbnailUrl) {
                        // restaurar imagem do livro usa a capa alternativa quando a imagem recebida falha
                        imagem.onerror = function () {
                            imagem.onerror = null;
                            imagem.src = item.dataset.fallbackThumbnail;
                        };
                        imagem.src = livro.thumbnailUrl.replace(/^http:/i, 'https:');
                    }
                });
            });
        }

        // reunir codigos de video extrai os identificadores para a consulta em lote
        const idsVideos = Array.from(new Set(itensVideo.map(function (item) { return item.dataset.videoId; })));
        for (let inicio = 0; inicio < idsVideos.length; inicio += 50) {
            const lote = idsVideos.slice(inicio, inicio + 50);
            // buscar detalhes dos videos tenta carregar um lote e atualizar os materiais correspondentes
            try {
                const parametros = new URLSearchParams({ ids: lote.join(',') });
                const resposta = await fetch('/youtube/detalhes?' + parametros.toString(), {
                    headers: { Accept: 'application/json' }
                });
                // conferir detalhes dos videos informa o codigo da falha quando a consulta nao tem sucesso
                if (!resposta.ok) {
                    throw new Error('Falha ao carregar as informações dos vídeos (' + resposta.status + ').');
                }
                const videos = await resposta.json();
                // relacionar video recebido associa o codigo aos detalhes consultados
                const videosPorId = new Map(videos.map(function (video) { return [video.videoId, video]; }));

                // localizar cartoes do video distribui os dados entre os itens do mesmo video
                lote.forEach(function (id) {
                    const video = videosPorId.get(id);
                    // comparar codigo de video seleciona os cartoes correspondentes ao video recebido
                    itensVideo.filter(function (item) { return item.dataset.videoId === id; })
                        // preencher video do conteudo mostra titulo canal e duracao ou informa indisponibilidade
                        .forEach(function (item) {
                            const titulo = item.querySelector('.conteudo-midia-titulo');
                            const detalhes = item.querySelector('.conteudo-midia-detalhes');
                            // conferir video encontrado avisa quando o lote nao retorna dados do video
                            if (!video) {
                                // avisar video indisponivel altera o titulo apenas quando o elemento existe
                                if (titulo) titulo.textContent = 'Informações do vídeo indisponíveis.';
                                return;
                            }
                            // preencher titulo do video usa o titulo encontrado ou o nome padrao
                            if (titulo) {
                                titulo.textContent = video.titulo || 'Vídeo do YouTube';
                                titulo.title = video.titulo || '';
                            }
                            // preencher detalhes do video apresenta o canal e a duracao disponiveis
                            if (detalhes) detalhes.textContent = [video.canal, video.duracao].filter(Boolean).join(' · ');
                        });
                });
            } catch (erro) {
                console.error('Não foi possível carregar os detalhes dos vídeos.', erro);
                // identificar videos do lote seleciona os itens atingidos pela falha da consulta
                itensVideo.filter(function (item) { return lote.includes(item.dataset.videoId); })
                    // mostrar falha do video substitui o titulo pelo aviso de dados indisponiveis
                    .forEach(function (item) {
                        const titulo = item.querySelector('.conteudo-midia-titulo');
                        // avisar video indisponivel altera o titulo apenas quando o elemento existe
                        if (titulo) titulo.textContent = 'Informações do vídeo indisponíveis.';
                    });
            }
        }
    }

    // atualizar botao de conteudos controla carregamento nova tentativa e existencia de mais resultados
    function atualizarBotao() {
        botaoMostrarMais.disabled = carregando;
        botaoMostrarMais.classList.toggle('d-none', blocoConteudos.dataset.temMais !== 'true' && !filtrosPendentes);
        listaConteudos.setAttribute('aria-busy', String(carregando));

        // mostrar carregamento de materiais atualiza o botao enquanto a consulta esta em andamento
        if (carregando) {
            botaoMostrarMais.textContent = 'Carregando...';
        }
        // oferecer nova tentativa de materiais usa o texto de nova tentativa quando os filtros estao pendentes
        else if (filtrosPendentes) {
            botaoMostrarMais.textContent = 'Tentar novamente';
        } else {
            botaoMostrarMais.textContent = 'Mostrar mais 10';
        }
    }

    // carregar meus conteudos busca uma pagina substitui ou amplia a lista e descarta respostas antigas
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

        // carregar meus conteudos tenta buscar os materiais e atualizar a lista e os contadores
        try {
            const resposta = await fetch(url);

            // descartar resposta antiga de materiais ignora uma consulta substituida por outra mais recente
            if (requisicaoAtual !== numeroRequisicao) {
                return;
            }

            // seguir redirecionamento dos materiais abre o endereco indicado pelo servidor
            if (resposta.redirected) {
                window.location.href = resposta.url;
                return;
            }

            // conferir consulta dos materiais informa falha quando a resposta nao tem sucesso
            if (!resposta.ok) {
                throw new Error('Não foi possível carregar os conteúdos');
            }

            const html = await resposta.text();


            // descartar leitura antiga de materiais ignora o texto recebido se outra consulta ja foi iniciada
            if (requisicaoAtual !== numeroRequisicao) {
                return;
            }

            const documento = new DOMParser().parseFromString(html, 'text/html');
            const novoBloco = documento.getElementById('blocoConteudos');
            const novaLista = documento.getElementById('listaConteudos');

            // validar lista dos materiais rejeita respostas sem as areas esperadas de conteudos
            if (!novoBloco || !novaLista) {
                throw new Error('A lista de conteúdos não foi encontrada');
            }

            // atualizar lista dos materiais acrescenta outra pagina ou substitui os resultados atuais
            if (adicionar) {
                // adicionar cartao recebido coloca o conteudo da nova pagina ao final da lista
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
            // mostrar erro atual dos materiais exibe a falha apenas para a consulta mais recente
            if (requisicaoAtual === numeroRequisicao) {
                erroCarregamento.textContent = 'Não foi possível carregar os conteúdos. Clique no botão abaixo para tentar novamente.';
                erroCarregamento.classList.remove('d-none');
            }
        } finally {
            // finalizar consulta atual dos materiais libera o carregamento e atualiza o botao
            if (requisicaoAtual === numeroRequisicao) {
                carregando = false;
                atualizarBotao();
            }
        }
    }

    // preparar novos filtros invalida consultas antigas e sinaliza uma busca pendente
    function prepararFiltros() {
        clearTimeout(tempoBusca);
        numeroRequisicao++;
        filtrosPendentes = true;
        carregando = true;
        erroCarregamento.classList.add('d-none');
        atualizarBotao();
    }

    // aguardar busca dos meus conteudos prepara os filtros e espera a pausa na digitacao
    campoBusca.addEventListener('input', function () {
        prepararFiltros();
        // buscar primeira pagina carrega novamente a lista com os filtros atuais
        tempoBusca = setTimeout(function () {
            carregarConteudos(0, false);
        }, 350);
    });

    // aplicar situacao dos meus conteudos inicia a pesquisa assim que o filtro muda
    filtroStatus.addEventListener('change', function () {
        prepararFiltros();
        carregarConteudos(0, false);
    });

    // continuar lista de conteudos tenta novamente os filtros pendentes ou carrega a proxima pagina
    botaoMostrarMais.addEventListener('click', function () {
        // bloquear novo carregamento ignora o clique enquanto os materiais estao sendo buscados
        if (carregando) {
            return;
        }

        // reaplicar filtros pendentes reinicia a busca quando os filtros ainda nao foram aplicados
        if (filtrosPendentes) {
            carregarConteudos(0, false);
        }
        // carregar proxima pagina de materiais busca mais resultados quando a lista indica continuacao
        else if (blocoConteudos.dataset.temMais === 'true') {
            carregarConteudos(Number(blocoConteudos.dataset.proximaPagina), true);
        }
    });


    // confirmar delete conteudo cancela a remocao quando a pessoa nao confirma
    listaConteudos.addEventListener('submit', function (event) {
        // confirmar delete material identifica o envio do formulario de exclusao
        if (event.target.matches('.form-remover-conteudo')) {
            // cancelar delete material impede o envio quando a exclusao nao foi confirmada
            if (!confirm('Tem certeza que deseja remover este conteúdo?')) {
                event.preventDefault();
            }
        }
    });

    // alternar detalhes dos meus conteudos mostra ou oculta a area e atualiza a descricao do botao
    listaConteudos.addEventListener('click', function (event) {
        // localizar clique nos materiais ignora alvos que nao sao elementos da pagina
        if (!(event.target instanceof Element)) return;
        const botao = event.target.closest('.conteudo-alternar-detalhes');
        // alternar detalhes do material exige um botao dentro da lista de conteudos
        if (!botao || !listaConteudos.contains(botao)) return;

        const detalhesId = botao.getAttribute('aria-controls');
        const detalhes = detalhesId ? document.getElementById(detalhesId) : null;
        // localizar detalhes do material encerra quando a area de detalhes nao existe
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