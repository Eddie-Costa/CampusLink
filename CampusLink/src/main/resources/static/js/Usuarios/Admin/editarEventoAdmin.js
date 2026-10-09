
document.addEventListener("DOMContentLoaded", function () {

    const formulario = document.getElementById("formEditarEvento");

    const buscarTurma = document.getElementById("buscarTurma");
    const turmaEvento = document.getElementById("turmaEvento");
    const mensagemBuscaTurma = document.getElementById("mensagemBuscaTurma");

    const professorEvento = document.getElementById("professorEvento");

    const buscarConteudo = document.getElementById("buscarConteudo");
    const conteudosRelacionados = document.getElementById("conteudosRelacionados");
    const mensagemBuscaConteudo = document.getElementById("mensagemBuscaConteudo");

    const inicioEvento = document.getElementById("inicioEvento");
    const fimEvento = document.getElementById("fimEvento");

    if (!formulario || !turmaEvento || !professorEvento || !conteudosRelacionados) {
        return;
    }

    let turmaAtual = turmaEvento.value;
    let numeroCarregamento = 0;

    // permite pesquisar com ou sem acentos
    function normalizarTexto(texto) {
        return String(texto || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
    }

    // mostra uma mensagem dentro do campo de selecao
    function mostrarMensagem(lista, texto) {
        lista.innerHTML = "";

        const opcao = document.createElement("option");
        opcao.value = "";
        opcao.textContent = texto;
        opcao.disabled = true;

        lista.appendChild(opcao);
    }

    // pesquisa as opcoes mantendo as que ja foram selecionadas
    function filtrarOpcoes(campoBusca, lista, mensagem) {

        const pesquisa = normalizarTexto(campoBusca.value);
        let encontrados = 0;

        Array.from(lista.options).forEach(function (opcao) {

            if (!opcao.value) {
                return;
            }

            const corresponde = normalizarTexto(opcao.textContent).includes(pesquisa);

            opcao.hidden = !corresponde && !opcao.selected;

            if (corresponde) {
                encontrados++;
            }
        });

        mensagem.hidden = pesquisa === "" || encontrados > 0;
    }

    // configura os campos de pesquisa
    function configurarBusca(campoBusca, lista, mensagem) {

        campoBusca.addEventListener("input", function () {
            filtrarOpcoes(campoBusca, lista, mensagem);
        });

        lista.addEventListener("change", function () {
            filtrarOpcoes(campoBusca, lista, mensagem);
        });

        campoBusca.addEventListener("keydown", function (evento) {
            if (evento.key === "Enter") {
                evento.preventDefault();
                lista.focus();
            }
        });
    }

    // carrega os professores quando a turma for alterada
    async function carregarProfessores(turmaId, carregamentoAtual) {

        professorEvento.disabled = true;
        mostrarMensagem(professorEvento, "Carregando professores...");

        try {
            const resposta = await fetch("/admin/eventos/professores?turmaId=" + encodeURIComponent(turmaId));

            if (!resposta.ok) {
                throw new Error("Erro ao buscar professores");
            }

            const professores = await resposta.json();

            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            professorEvento.innerHTML = "";

            if (!Array.isArray(professores) || professores.length === 0) {
                mostrarMensagem(professorEvento, "Nenhum professor ativo nesta turma");
                return;
            }

            const primeiraOpcao = document.createElement("option");
            primeiraOpcao.value = "";
            primeiraOpcao.textContent = "Selecione um professor";
            professorEvento.appendChild(primeiraOpcao);

            professores.forEach(function (professor) {

                const opcao = document.createElement("option");
                opcao.value = professor.idPerfil;
                opcao.textContent = professor.nome;

                professorEvento.appendChild(opcao);
            });

            professorEvento.value = "";
            professorEvento.disabled = false;

        } catch (erro) {

            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            console.error("Erro ao carregar professores:", erro);

            mostrarMensagem(professorEvento, "Não foi possível carregar os professores");
            professorEvento.disabled = true;
        }
    }

    // carrega os conteudos da nova turma
    async function carregarConteudos(turmaId, carregamentoAtual) {

        conteudosRelacionados.disabled = true;
        buscarConteudo.disabled = true;

        mostrarMensagem(conteudosRelacionados, "Carregando conteúdos...");

        try {
            const resposta = await fetch("/admin/eventos/conteudos?turmaId=" + encodeURIComponent(turmaId));

            if (!resposta.ok) {
                throw new Error("Erro ao buscar conteúdos");
            }

            const conteudos = await resposta.json();

            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            conteudosRelacionados.innerHTML = "";

            if (!Array.isArray(conteudos) || conteudos.length === 0) {
                mostrarMensagem(conteudosRelacionados, "Nenhum conteúdo cadastrado nesta turma");
                return;
            }

            conteudos.forEach(function (conteudo) {

                const opcao = document.createElement("option");
                opcao.value = conteudo.id;
                opcao.textContent = conteudo.titulo;

                conteudosRelacionados.appendChild(opcao);
            });

            conteudosRelacionados.disabled = false;
            buscarConteudo.disabled = false;

        } catch (erro) {

            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            console.error("Erro ao carregar conteúdos:", erro);

            mostrarMensagem(conteudosRelacionados, "Não foi possível carregar os conteúdos");

            conteudosRelacionados.disabled = true;
            buscarConteudo.disabled = true;
        }
    }

    // atualiza os campos somente quando a turma mudar
    function atualizarTurma() {

        const turmaId = turmaEvento.value;

        if (turmaId === turmaAtual) {
            return;
        }

        turmaAtual = turmaId;
        numeroCarregamento++;

        const carregamentoAtual = numeroCarregamento;

        professorEvento.disabled = true;
        conteudosRelacionados.disabled = true;

        buscarConteudo.value = "";
        buscarConteudo.disabled = true;
        mensagemBuscaConteudo.hidden = true;

        if (!turmaId) {
            mostrarMensagem(professorEvento, "Selecione uma turma primeiro");
            mostrarMensagem(conteudosRelacionados, "Selecione uma turma primeiro");
            return;
        }

        carregarProfessores(turmaId, carregamentoAtual);
        carregarConteudos(turmaId, carregamentoAtual);
    }

    // valida as datas do evento
    function validarDatas() {

        const inicio = inicioEvento.value;
        const fim = fimEvento.value;

        fimEvento.min = inicio || "";

        if (inicio && fim && fim < inicio) {
            fimEvento.setCustomValidity("A data final não pode ser anterior ao início.");
        } else {
            fimEvento.setCustomValidity("");
        }
    }

    configurarBusca(buscarTurma, turmaEvento, mensagemBuscaTurma);
    configurarBusca(buscarConteudo, conteudosRelacionados, mensagemBuscaConteudo);

    turmaEvento.addEventListener("change", atualizarTurma);

    inicioEvento.addEventListener("change", validarDatas);
    fimEvento.addEventListener("change", validarDatas);

    formulario.addEventListener("submit", function (evento) {

        validarDatas();

        if (!formulario.checkValidity()) {
            evento.preventDefault();
            formulario.reportValidity();
            return;
        }

        if (!professorEvento.value) {
            evento.preventDefault();
            professorEvento.focus();
        }
    });

    // mantem os dados originais quando a pagina abre
    validarDatas();
});
