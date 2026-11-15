// lista administrativa de eventos busca filtra e carrega mais resultados pelo servidor

// preparar lista administrativa de eventos configura busca tipo situacao e carregamento de mais itens
document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("buscarEvento");
    const filtroTipo = document.getElementById("filtrarTipoEvento");
    const filtroStatus = document.getElementById("filtrarStatusEvento");
    const corpo = document.getElementById("corpoEventos");
    const botaoMostrarMais = document.getElementById("botaoMostrarMais");
    const erroCarregamento = document.getElementById("erroCarregamento");

    // iniciar lista de eventos exige os filtros e o corpo da tabela
    if (!campoBusca || !filtroTipo || !filtroStatus || !corpo) {
        return;
    }

    let tempoBusca;
    let requisicaoAtual = null;

    // mostrar erro de carregamento exibe a mensagem na area de falhas dos eventos
    function mostrarErro(mensagem) {
        // mostrar erro dos eventos preenche e exibe a area de falha disponivel
        if (erroCarregamento) {
            erroCarregamento.textContent = mensagem;
            erroCarregamento.classList.remove("d-none");
        }
    }

    // limpar erro de carregamento apaga a mensagem e oculta a area de falhas
    function limparErro() {
        // limpar erro dos eventos apaga a mensagem e oculta a area de falha
        if (erroCarregamento) {
            erroCarregamento.textContent = "";
            erroCarregamento.classList.add("d-none");
        }
    }

    // montar busca de eventos combina pagina texto tipo e situacao no endereco da consulta
    function montarUrl(pagina) {
        const url = new URL("/admin/eventos", window.location.origin);

        url.searchParams.set("pagina", pagina);
        url.searchParams.set("busca", campoBusca.value.trim());
        url.searchParams.set("tipo", filtroTipo.value);
        url.searchParams.set("status", filtroStatus.value);
        url.searchParams.set("fragmento", "true");

        return url.toString();
    }

    // ler linhas recebidas monta a resposta dentro de uma tabela para o navegador nao descartar as linhas
    function lerCorpoResposta(html) {
        const documento = new DOMParser().parseFromString("<table>" + html + "</table>", "text/html");
        const novoCorpo = documento.querySelector("tbody");

        // validar resposta dos eventos rejeita uma resposta sem o corpo da tabela
        if (!novoCorpo) {
            throw new Error("Resposta de eventos inválida");
        }

        return novoCorpo;
    }

    // atualizar contadores mostra quantos eventos estao na tela e quantos correspondem aos filtros
    function atualizarContadores() {
        const exibidos = corpo.querySelectorAll(".evento-admin-linha").length;
        const totalExibido = document.getElementById("totalExibido");
        const totalEncontrado = document.getElementById("totalEncontrado");

        // mostrar quantidade exibida atualiza o contador dos eventos na tela
        if (totalExibido) {
            totalExibido.textContent = exibidos;
        }

        // mostrar quantidade encontrada atualiza o total correspondente aos filtros
        if (totalEncontrado) {
            totalEncontrado.textContent = corpo.dataset.total || 0;
        }

        // controlar mais eventos exibe o botao apenas quando existem outras paginas
        if (botaoMostrarMais) {
            botaoMostrarMais.classList.toggle("d-none", corpo.dataset.temMais !== "true");
        }
    }

    // atualizar endereco dos filtros grava a busca no endereco sem recarregar a pagina
    function atualizarEndereco() {
        const url = new URL(window.location.href);
        const valores = { busca: campoBusca.value.trim(), tipo: filtroTipo.value, status: filtroStatus.value };

        url.searchParams.delete("pagina");
        url.searchParams.delete("fragmento");

        Object.keys(valores).forEach(function (nome) {
            // atualizar filtro no endereco inclui o valor preenchido ou retira o parametro vazio
            if (valores[nome]) {
                url.searchParams.set(nome, valores[nome]);
            } else {
                url.searchParams.delete(nome);
            }
        });

        window.history.replaceState(null, "", url.pathname + url.search + url.hash);
    }

    // consultar eventos busca uma pagina no servidor e substitui ou acrescenta as linhas no mesmo corpo da tabela
    async function consultar(pagina, acrescentar) {

        // cancelar consulta anterior interrompe a busca em andamento quando os filtros mudam
        if (requisicaoAtual) {
            // ignorar novo pedido de mais itens espera a consulta atual terminar
            if (acrescentar) {
                return;
            }
            requisicaoAtual.abort();
        }

        const requisicao = new AbortController();
        requisicaoAtual = requisicao;

        corpo.setAttribute("aria-busy", "true");
        limparErro();

        // mostrar carregamento troca o texto do botao enquanto a proxima pagina chega
        if (acrescentar && botaoMostrarMais) {
            botaoMostrarMais.disabled = true;
            botaoMostrarMais.textContent = "Carregando...";
        }

        // carregar eventos tenta trazer as linhas e atualizar contadores e endereco
        try {
            const resposta = await fetch(montarUrl(pagina), {
                signal: requisicao.signal,
                headers: { "X-Requested-With": "XMLHttpRequest" }
            });

            // detectar falha de eventos interrompe a leitura quando o servidor nao confirma a consulta
            if (!resposta.ok) {
                throw new Error("Erro ao buscar eventos");
            }

            const novoCorpo = lerCorpoResposta(await resposta.text());

            // descartar consulta cancelada ignora a resposta quando outra busca ja comecou
            if (requisicao.signal.aborted) {
                return;
            }

            // manter o mesmo corpo da tabela preserva a ordenacao por coluna da tabela inteligente
            if (!acrescentar) {
                corpo.replaceChildren();
            }
            Array.from(novoCorpo.rows).forEach(function (linha) {
                // acrescentar linha de evento ignora avisos de lista vazia ao mostrar mais
                if (!acrescentar || linha.classList.contains("evento-admin-linha")) {
                    corpo.appendChild(linha);
                }
            });

            corpo.dataset.total = novoCorpo.dataset.total;
            corpo.dataset.temMais = novoCorpo.dataset.temMais;
            corpo.dataset.proximaPagina = novoCorpo.dataset.proximaPagina;

            atualizarContadores();

            // registrar filtros no endereco permite recarregar ou compartilhar a mesma busca
            if (!acrescentar) {
                atualizarEndereco();
            }

        } catch (erro) {
            // avisar falha mostra o erro quando a consulta falha sem cancelamento
            if (erro.name !== "AbortError") {
                mostrarErro("Não foi possível carregar os eventos. Tente novamente.");
            }

        } finally {
            // finalizar consulta atual limpa o controle apenas se ela ainda e a mais recente
            if (requisicaoAtual === requisicao) {
                requisicaoAtual = null;
                corpo.removeAttribute("aria-busy");
            }

            // liberar botao devolve o texto original depois de carregar mais eventos
            if (acrescentar && botaoMostrarMais) {
                botaoMostrarMais.disabled = false;
                botaoMostrarMais.textContent = "Mostrar mais 25";
            }
        }
    }

    // aguardar digitacao reinicia o tempo de espera para nao consultar a cada letra
    campoBusca.addEventListener("input", function () {
        clearTimeout(tempoBusca);
        tempoBusca = setTimeout(function () {
            consultar(0, false);
        }, 350);
    });

    // alterar tipo ou situacao busca imediatamente
    [filtroTipo, filtroStatus].forEach(function (filtro) {
        filtro.addEventListener("change", function () {
            clearTimeout(tempoBusca);
            consultar(0, false);
        });
    });

    // mostrar mais eventos carrega a proxima pagina ao clicar no botao
    if (botaoMostrarMais) {
        botaoMostrarMais.addEventListener("click", function () {
            consultar(Number(corpo.dataset.proximaPagina || 1), true);
        });
    }

    atualizarContadores();
});
