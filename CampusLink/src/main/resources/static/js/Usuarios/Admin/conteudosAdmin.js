
document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("campoBusca");
    const filtroStatus = document.getElementById("filtroStatus");
    const totalConteudos = document.getElementById("totalConteudos");
    const erroCarregamento = document.getElementById("erroCarregamento");

    if (!campoBusca || !filtroStatus || !document.getElementById("blocoConteudos")) {
        return;
    }

    let tempoBusca;
    let requisicaoFiltro = null;
    let requisicaoMais = null;

    function mostrarErro(mensagem) {
        if (erroCarregamento) {
            erroCarregamento.textContent = mensagem;
            erroCarregamento.classList.remove("d-none");
        }
    }

    function limparErro() {
        if (erroCarregamento) {
            erroCarregamento.textContent = "";
            erroCarregamento.classList.add("d-none");
        }
    }

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

    function obterBlocoResposta(html) {
        const documento = new DOMParser().parseFromString(html, "text/html");
        const bloco = documento.getElementById("blocoConteudos");

        if (!bloco) {
            throw new Error("Resposta de conteúdos inválida");
        }

        return bloco;
    }

    function atualizarContadores() {
        const bloco = document.getElementById("blocoConteudos");

        if (!bloco) {
            return;
        }

        const total = Number(bloco.dataset.total || 0);
        const exibidos = bloco.querySelectorAll(".conteudo-admin-card").length;

        const totalExibido = bloco.querySelector("#totalExibido");
        const totalEncontrado = bloco.querySelector("#totalEncontrado");
        const botaoMostrarMais = bloco.querySelector("#botaoMostrarMais");

        if (totalConteudos) {
            totalConteudos.textContent = total;
        }

        if (totalExibido) {
            totalExibido.textContent = exibidos;
        }

        if (totalEncontrado) {
            totalEncontrado.textContent = total;
        }

        if (botaoMostrarMais) {
            botaoMostrarMais.classList.toggle("d-none", bloco.dataset.temMais !== "true");
        }
    }

    function atualizarEndereco() {
        const url = new URL(window.location.href);
        const busca = campoBusca.value.trim();
        const status = filtroStatus.value;

        url.searchParams.delete("pagina");
        url.searchParams.delete("fragmento");

        if (busca) {
            url.searchParams.set("busca", busca);
        } else {
            url.searchParams.delete("busca");
        }

        if (status !== "todos") {
            url.searchParams.set("status", status);
        } else {
            url.searchParams.delete("status");
        }

        window.history.replaceState(null, "", url.pathname + url.search + url.hash);
    }

    // atualiza os conteudos quando a busca ou o filtro muda
    async function buscarConteudos() {

        if (requisicaoFiltro) {
            requisicaoFiltro.abort();
        }

        if (requisicaoMais) {
            requisicaoMais.abort();
            requisicaoMais = null;
        }

        const requisicao = new AbortController();
        requisicaoFiltro = requisicao;

        const bloco = document.getElementById("blocoConteudos");
        bloco.setAttribute("aria-busy", "true");

        limparErro();

        try {
            const resposta = await fetch(montarUrl(0), {
                signal: requisicao.signal,
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                }
            });

            if (!resposta.ok) {
                throw new Error("Erro ao buscar conteúdos");
            }

            const html = await resposta.text();
            const novoBloco = obterBlocoResposta(html);

            if (requisicao.signal.aborted) {
                return;
            }

            document.getElementById("blocoConteudos").replaceWith(novoBloco);

            atualizarContadores();
            atualizarEndereco();

        } catch (erro) {
            if (erro.name !== "AbortError") {
                mostrarErro("Não foi possível atualizar os conteúdos. Tente novamente.");
            }

        } finally {
            if (requisicaoFiltro === requisicao) {
                requisicaoFiltro = null;

                const blocoAtual = document.getElementById("blocoConteudos");

                if (blocoAtual) {
                    blocoAtual.removeAttribute("aria-busy");
                }
            }
        }
    }

    // adiciona mais dez conteudos sem apagar os anteriores
    async function mostrarMaisConteudos() {

        if (requisicaoFiltro || requisicaoMais) {
            return;
        }

        const bloco = document.getElementById("blocoConteudos");
        const lista = bloco.querySelector("#listaConteudos");
        const botao = bloco.querySelector("#botaoMostrarMais");

        if (!lista || !botao || bloco.dataset.temMais !== "true") {
            return;
        }

        const pagina = Number(bloco.dataset.proximaPagina || 1);

        const requisicao = new AbortController();
        requisicaoMais = requisicao;

        botao.disabled = true;
        botao.textContent = "Carregando...";

        limparErro();

        try {
            const resposta = await fetch(montarUrl(pagina), {
                signal: requisicao.signal,
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                }
            });

            if (!resposta.ok) {
                throw new Error("Erro ao carregar mais conteúdos");
            }

            const html = await resposta.text();
            const novoBloco = obterBlocoResposta(html);
            const novosConteudos = novoBloco.querySelectorAll(".conteudo-admin-card");

            if (requisicao.signal.aborted) {
                return;
            }

            novosConteudos.forEach(function (conteudo) {
                lista.appendChild(conteudo);
            });

            bloco.dataset.proximaPagina = novoBloco.dataset.proximaPagina;
            bloco.dataset.temMais = novoBloco.dataset.temMais;
            bloco.dataset.total = novoBloco.dataset.total;

            atualizarContadores();

        } catch (erro) {
            if (erro.name !== "AbortError") {
                mostrarErro("Não foi possível carregar mais conteúdos. Tente novamente.");
            }

        } finally {
            if (requisicaoMais === requisicao) {
                requisicaoMais = null;
            }

            botao.disabled = false;
            botao.textContent = "Mostrar mais 10";
        }
    }

    // espera o usuario terminar de digitar antes de buscar
    campoBusca.addEventListener("input", function () {
        clearTimeout(tempoBusca);

        tempoBusca = setTimeout(function () {
            buscarConteudos();
        }, 350);
    });

    filtroStatus.addEventListener("change", function () {
        clearTimeout(tempoBusca);
        buscarConteudos();
    });

    // funciona mesmo depois que a lista e atualizada
    document.addEventListener("click", function (evento) {
        if (evento.target.id === "botaoMostrarMais") {
            mostrarMaisConteudos();
        }
    });

    atualizarContadores();
});
