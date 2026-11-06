// edicao administrativa de evento atualiza opcoes da turma e valida datas e professor

// preparar edicao administrativa configura buscas dependentes da turma e validacao de datas
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

    // configurar edicao administrativa de evento exige o formulario e seus seletores
    if (!formulario || !turmaEvento || !professorEvento || !conteudosRelacionados) {
        return;
    }

    let turmaAtual = turmaEvento.value;
    let numeroCarregamento = 0;


    // normalizar filtro da edicao remove acentos espacos externos e letras maiusculas
    function normalizarTexto(texto) {
        return String(texto || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
    }


    // mostrar aviso da edicao substitui a lista por uma mensagem desativada
    function mostrarMensagem(lista, texto) {
        lista.innerHTML = "";

        const opcao = document.createElement("option");
        opcao.value = "";
        opcao.textContent = texto;
        opcao.disabled = true;

        lista.appendChild(opcao);
    }


    // filtrar opcoes da edicao busca nomes e informa quando nada corresponde
    function filtrarOpcoes(campoBusca, lista, mensagem) {

        const pesquisa = normalizarTexto(campoBusca.value);
        let encontrados = 0;

        // comparar opcao da edicao mantem os itens selecionados mesmo fora da busca
        Array.from(lista.options).forEach(function (opcao) {

            // ignorar opcao sem codigo deixa a mensagem inicial fora da busca
            if (!opcao.value) {
                return;
            }

            const corresponde = normalizarTexto(opcao.textContent).includes(pesquisa);

            opcao.hidden = !corresponde && !opcao.selected;

            // contar opcao encontrada aumenta o total quando o texto corresponde a busca
            if (corresponde) {
                encontrados++;
            }
        });

        mensagem.hidden = pesquisa === "" || encontrados > 0;
    }


    // configurar busca da edicao acompanha texto selecao e tecla enter
    function configurarBusca(campoBusca, lista, mensagem) {

        // filtrar edicao por texto atualiza as opcoes enquanto a pessoa digita
        campoBusca.addEventListener("input", function () {
            filtrarOpcoes(campoBusca, lista, mensagem);
        });

        // filtrar edicao por selecao atualiza a busca preservando as escolhas
        lista.addEventListener("change", function () {
            filtrarOpcoes(campoBusca, lista, mensagem);
        });

        // focar lista da edicao impede o envio por enter e leva o foco as opcoes
        campoBusca.addEventListener("keydown", function (evento) {
            // acessar resultados da edicao move o foco para a lista ao pressionar enter
            if (evento.key === "Enter") {
                evento.preventDefault();
                lista.focus();
            }
        });
    }


    // buscar professores para edicao carrega os professores ativos da nova turma sem preservar a escolha anterior
    async function carregarProfessores(turmaId, carregamentoAtual) {

        professorEvento.disabled = true;
        mostrarMensagem(professorEvento, "Carregando professores...");

        // buscar professores para edicao tenta carregar as opcoes da turma escolhida
        try {
            const resposta = await fetch("/admin/eventos/professores?turmaId=" + encodeURIComponent(turmaId));

            // conferir professores na edicao informa falha quando a consulta nao tem sucesso
            if (!resposta.ok) {
                throw new Error("Erro ao buscar professores");
            }

            const professores = await resposta.json();

            // descartar resposta antiga interrompe a atualizacao quando outra consulta ja foi iniciada
            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            professorEvento.innerHTML = "";

            // mostrar edicao sem professores avisa quando a turma nao possui professores ativos
            if (!Array.isArray(professores) || professores.length === 0) {
                mostrarMensagem(professorEvento, "Nenhum professor ativo nesta turma");
                return;
            }

            const primeiraOpcao = document.createElement("option");
            primeiraOpcao.value = "";
            primeiraOpcao.textContent = "Selecione um professor";
            professorEvento.appendChild(primeiraOpcao);

            // montar professor da edicao adiciona nome e codigo do perfil a lista
            professores.forEach(function (professor) {

                const opcao = document.createElement("option");
                opcao.value = professor.idPerfil;
                opcao.textContent = professor.nome;

                professorEvento.appendChild(opcao);
            });

            professorEvento.value = "";
            professorEvento.disabled = false;

        } catch (erro) {

            // descartar resposta antiga interrompe a atualizacao quando outra consulta ja foi iniciada
            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            console.error("Erro ao carregar professores:", erro);

            mostrarMensagem(professorEvento, "Não foi possível carregar os professores");
            professorEvento.disabled = true;
        }
    }


    // buscar conteudos para edicao consulta os itens da nova turma e controla os campos de busca
    async function carregarConteudos(turmaId, carregamentoAtual) {

        conteudosRelacionados.disabled = true;
        buscarConteudo.disabled = true;

        mostrarMensagem(conteudosRelacionados, "Carregando conteúdos...");

        // buscar conteudos para edicao tenta carregar os materiais da turma escolhida
        try {
            const resposta = await fetch("/admin/eventos/conteudos?turmaId=" + encodeURIComponent(turmaId));

            // detectar falha de conteudos interrompe a leitura quando o servidor nao confirma a consulta
            if (!resposta.ok) {
                throw new Error("Erro ao buscar conteúdos");
            }

            const conteudos = await resposta.json();

            // descartar resposta antiga interrompe a atualizacao quando outra consulta ja foi iniciada
            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            conteudosRelacionados.innerHTML = "";

            // mostrar edicao sem conteudos avisa quando a turma nao possui materiais
            if (!Array.isArray(conteudos) || conteudos.length === 0) {
                mostrarMensagem(conteudosRelacionados, "Nenhum conteúdo cadastrado nesta turma");
                return;
            }

            // montar conteudo da edicao adiciona o titulo e o codigo recebido a lista
            conteudos.forEach(function (conteudo) {

                const opcao = document.createElement("option");
                opcao.value = conteudo.id;
                opcao.textContent = conteudo.titulo;

                conteudosRelacionados.appendChild(opcao);
            });

            conteudosRelacionados.disabled = false;
            buscarConteudo.disabled = false;

        } catch (erro) {

            // descartar resposta antiga interrompe a atualizacao quando outra consulta ja foi iniciada
            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            console.error("Erro ao carregar conteúdos:", erro);

            mostrarMensagem(conteudosRelacionados, "Não foi possível carregar os conteúdos");

            conteudosRelacionados.disabled = true;
            buscarConteudo.disabled = true;
        }
    }


    // alterar turma na edicao recarrega professores e conteudos apenas quando a turma muda
    function atualizarTurma() {

        const turmaId = turmaEvento.value;

        // evitar consulta da mesma turma ignora uma selecao que nao mudou
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

        // exigir turma na edicao orienta selecionar uma turma antes de buscar professores e conteudos
        if (!turmaId) {
            mostrarMensagem(professorEvento, "Selecione uma turma primeiro");
            mostrarMensagem(conteudosRelacionados, "Selecione uma turma primeiro");
            return;
        }

        carregarProfessores(turmaId, carregamentoAtual);
        carregarConteudos(turmaId, carregamentoAtual);
    }


    // validar datas da edicao impede que o termino fique antes do inicio
    function validarDatas() {

        const inicio = inicioEvento.value;
        const fim = fimEvento.value;

        fimEvento.min = inicio || "";

        // validar datas da edicao rejeita termino anterior ao inicio e limpa o erro nos demais casos
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

    // enviar edicao administrativa valida o formulario e exige a escolha do professor
    formulario.addEventListener("submit", function (evento) {

        validarDatas();

        // validar formulario antes do envio cancela a solicitacao e mostra os campos invalidos
        if (!formulario.checkValidity()) {
            evento.preventDefault();
            formulario.reportValidity();
            return;
        }

        // exigir professor na edicao impede o envio e foca o seletor quando nao ha professor
        if (!professorEvento.value) {
            evento.preventDefault();
            professorEvento.focus();
        }
    });


    validarDatas();
});
