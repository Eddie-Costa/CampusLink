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

    atualizarBotao();
});