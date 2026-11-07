// lista administrativa de conteudos permite buscar filtrar e carregar mais resultados

// preparar lista administrativa de conteudos configura busca filtro e carregamento de mais itens
document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("campoBusca");
    const filtroStatus = document.getElementById("filtroStatus");
    const totalConteudos = document.getElementById("totalConteudos");
    const erroCarregamento = document.getElementById("erroCarregamento");

    // iniciar lista administrativa de conteudos exige os filtros e a area de resultados
    if (!campoBusca || !filtroStatus || !document.getElementById("blocoConteudos")) {
        return;
    }

    let tempoBusca;
    let requisicaoFiltro = null;
    let requisicaoMais = null;

    // mostrar erro de carregamento exibe a mensagem na area de falhas dos conteudos
    function mostrarErro(mensagem) {
        // mostrar erro dos conteudos preenche e exibe a area de falha disponivel
        if (erroCarregamento) {
            erroCarregamento.textContent = mensagem;
            erroCarregamento.classList.remove("d-none");
        }
    }

    // limpar erro de carregamento apaga a mensagem e oculta a area de falhas
    function limparErro() {
        // limpar erro dos conteudos apaga a mensagem e oculta a area de falha
        if (erroCarregamento) {
            erroCarregamento.textContent = "";
            erroCarregamento.classList.add("d-none");
        }
    }

    // montar busca de conteudos combina pagina texto e situacao no endereco da consulta
    function montarUrl(pagina) {
        const bloco = document.getElementById("blocoConteudos");
        const endereco = bloco.dataset.url || "/admin/conteudos";
        const url = new URL(endereco, window.location.origin);

        url.searchParams.set("pagina", pagina);
        url.searchParams.set("busca", campoBusca.value.trim());
        url.searchParams.set("status", filtroStatus.value);
        url.searchParams.set("fragmento", "true");

        return url.toString();
    }

    // ler lista recebida extrai a area de conteudos da pagina retornada e rejeita respostas invalidas
    function obterBlocoResposta(html) {
        const documento = new DOMParser().parseFromString(html, "text/html");
        const bloco = documento.getElementById("blocoConteudos");

        // validar resposta dos conteudos rejeita uma resposta sem a area esperada
        if (!bloco) {
            throw new Error("Resposta de conteúdos inválida");
        }

        return bloco;
    }

    // atualizar totais de conteudos mostra quantos itens existem e quantos estao visiveis
    function atualizarContadores() {
        const bloco = document.getElementById("blocoConteudos");

        // contar conteudos exibidos encerra quando a area de resultados nao existe
        if (!bloco) {
            return;
        }

        const total = Number(bloco.dataset.total || 0);
        const exibidos = bloco.querySelectorAll(".conteudo-admin-card").length;

        const totalExibido = bloco.querySelector("#totalExibido");
        const totalEncontrado = bloco.querySelector("#totalEncontrado");
        const botaoMostrarMais = bloco.querySelector("#botaoMostrarMais");

        // mostrar total de conteudos atualiza o contador geral disponivel
        if (totalConteudos) {
            totalConteudos.textContent = total;
        }

        // mostrar quantidade exibida atualiza o contador dos conteudos na tela
        if (totalExibido) {
            totalExibido.textContent = exibidos;
        }

        // mostrar quantidade encontrada atualiza o total correspondente aos filtros
        if (totalEncontrado) {
            totalEncontrado.textContent = total;
        }

        // controlar mais conteudos exibe o botao apenas quando existem outras paginas
        if (botaoMostrarMais) {
            botaoMostrarMais.classList.toggle("d-none", bloco.dataset.temMais !== "true");
        }
    }

    // atualizar endereco dos filtros grava busca e situacao no endereco sem recarregar a pagina
    function atualizarEndereco() {
        const url = new URL(window.location.href);
        const busca = campoBusca.value.trim();
        const status = filtroStatus.value;

        url.searchParams.delete("pagina");
        url.searchParams.delete("fragmento");

        // atualizar busca no endereco inclui o termo preenchido ou retira o parametro vazio
        if (busca) {
            url.searchParams.set("busca", busca);
        } else {
            url.searchParams.delete("busca");
        }

        // atualizar situacao no endereco inclui o filtro especifico ou retira o filtro de todos
        if (status !== "todos") {
            url.searchParams.set("status", status);
        } else {
            url.searchParams.delete("status");
        }

        window.history.replaceState(null, "", url.pathname + url.search + url.hash);
    }


    // buscar conteudos filtrados cancela buscas anteriores e substitui a lista pelos novos resultados
    async function buscarConteudos() {

        // cancelar filtro anterior interrompe a consulta de conteudos em andamento
        if (requisicaoFiltro) {
            requisicaoFiltro.abort();
        }

        // cancelar pagina anterior interrompe o carregamento de mais conteudos ao mudar o filtro
        if (requisicaoMais) {
            requisicaoMais.abort();
            requisicaoMais = null;
        }

        const requisicao = new AbortController();
        requisicaoFiltro = requisicao;

        const bloco = document.getElementById("blocoConteudos");
        bloco.setAttribute("aria-busy", "true");

        limparErro();

        // consultar conteudos filtrados tenta carregar a lista e atualizar os contadores e o endereco
        try {
            const resposta = await fetch(montarUrl(0), {
                signal: requisicao.signal,
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                }
            });

            // detectar falha de conteudos interrompe a leitura quando o servidor nao confirma a consulta
            if (!resposta.ok) {
                throw new Error("Erro ao buscar conteúdos");
            }

            const html = await resposta.text();
            const novoBloco = obterBlocoResposta(html);

            // descartar filtro cancelado ignora a resposta quando a consulta foi interrompida
            if (requisicao.signal.aborted) {
                return;
            }

            document.getElementById("blocoConteudos").replaceWith(novoBloco);

            atualizarContadores();
            atualizarEndereco();

        } catch (erro) {
            // avisar falha no filtro mostra o erro quando a consulta falha sem cancelamento
            if (erro.name !== "AbortError") {
                mostrarErro("Não foi possível atualizar os conteúdos. Tente novamente.");
            }

        } finally {
            // finalizar filtro atual limpa o controle apenas se a consulta ainda e a atual
            if (requisicaoFiltro === requisicao) {
                requisicaoFiltro = null;

                const blocoAtual = document.getElementById("blocoConteudos");

                // liberar area de conteudos retira a indicacao de carregamento da lista atual
                if (blocoAtual) {
                    blocoAtual.removeAttribute("aria-busy");
                }
            }
        }
    }


    // carregar mais conteudos consulta a proxima pagina e acrescenta os itens a lista atual
    async function mostrarMaisConteudos() {

        // evitar consultas simultaneas bloqueia mais resultados enquanto ha uma busca em andamento
        if (requisicaoFiltro || requisicaoMais) {
            return;
        }

        const bloco = document.getElementById("blocoConteudos");
        const lista = bloco.querySelector("#listaConteudos");
        const botao = bloco.querySelector("#botaoMostrarMais");

        // conferir proxima pagina exige lista botao e indicacao de mais conteudos
        if (!lista || !botao || bloco.dataset.temMais !== "true") {
            return;
        }

        const pagina = Number(bloco.dataset.proximaPagina || 1);

        const requisicao = new AbortController();
        requisicaoMais = requisicao;

        botao.disabled = true;
        botao.textContent = "Carregando...";

        limparErro();

        // carregar mais conteudos tenta acrescentar os resultados e atualizar os dados da paginacao
        try {
            const resposta = await fetch(montarUrl(pagina), {
                signal: requisicao.signal,
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                }
            });

            // conferir pagina de conteudos informa falha quando o servidor recusa a consulta
            if (!resposta.ok) {
                throw new Error("Erro ao carregar mais conteúdos");
            }

            const html = await resposta.text();
            const novoBloco = obterBlocoResposta(html);
            const novosConteudos = novoBloco.querySelectorAll(".conteudo-admin-card");

            // descartar pagina cancelada ignora resultados de um carregamento interrompido
            if (requisicao.signal.aborted) {
                return;
            }

            // acrescentar conteudo recebido coloca o novo cartao ao final da lista
            novosConteudos.forEach(function (conteudo) {
                lista.appendChild(conteudo);
            });

            bloco.dataset.proximaPagina = novoBloco.dataset.proximaPagina;
            bloco.dataset.temMais = novoBloco.dataset.temMais;
            bloco.dataset.total = novoBloco.dataset.total;

            atualizarContadores();

        } catch (erro) {
            // avisar falha na paginacao mostra o erro quando a consulta falha sem cancelamento
            if (erro.name !== "AbortError") {
                mostrarErro("Não foi possível carregar mais conteúdos. Tente novamente.");
            }

        } finally {
            // finalizar pagina atual limpa o controle apenas se a consulta ainda e a atual
            if (requisicaoMais === requisicao) {
                requisicaoMais = null;
            }

            botao.disabled = false;
            botao.textContent = "Mostrar mais 10";
        }
    }


    // aguardar busca administrativa reinicia o tempo de espera ao digitar
    campoBusca.addEventListener("input", function () {
        clearTimeout(tempoBusca);

        // executar busca aguardada consulta os conteudos depois da pausa na digitacao
        tempoBusca = setTimeout(function () {
            buscarConteudos();
        }, 350);
    });

    // alterar filtro de situacao cancela a espera e busca imediatamente
    filtroStatus.addEventListener("change", function () {
        clearTimeout(tempoBusca);
        buscarConteudos();
    });


    // mostrar mais por clique identifica o botao mesmo depois da atualizacao da lista
    document.addEventListener("click", function (evento) {
        // acionar mais conteudos carrega outra pagina ao clicar no botao correspondente
        if (evento.target.id === "botaoMostrarMais") {
            mostrarMaisConteudos();
        }
    });

    atualizarContadores();
});
