// cadastro administrativo de evento carrega professores e conteudos da turma e valida o envio
// preparar cadastro administrativo configura buscas professores conteudos datas e bloqueio de envio
document.addEventListener("DOMContentLoaded", function () {

    const formulario = document.getElementById("formCadastrarEvento");

    const buscarTurma = document.getElementById("buscarTurma");
    const turmaEvento = document.getElementById("turmaEvento");
    const mensagemBuscaTurma = document.getElementById("mensagemBuscaTurma");

    const professorEvento = document.getElementById("professorEvento");

    const buscarConteudo = document.getElementById("buscarConteudo");
    const conteudosRelacionados = document.getElementById("conteudosRelacionados");
    const mensagemBuscaConteudo = document.getElementById("mensagemBuscaConteudo");

    const inicioEvento = document.getElementById("inicioEvento");
    const fimEvento = document.getElementById("fimEvento");

    // configurar cadastro administrativo de evento exige o formulario e seus seletores
    if (!formulario || !turmaEvento || !professorEvento || !conteudosRelacionados) {
        return;
    }

    const botaoCadastrar = formulario.querySelector('button[type="submit"]');
    const textoOriginalBotao = botaoCadastrar ? botaoCadastrar.textContent : "";
    const botaoDesabilitadoInicialmente = botaoCadastrar ? botaoCadastrar.disabled : false;

    let numeroCarregamento = 0;
    let enviando = false;


    // normalizar busca administrativa remove acentos e espacos e usa letras minusculas
    function normalizarTexto(texto) {
        return String(texto || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
    }


    // mostrar aviso na selecao substitui as opcoes por uma mensagem desativada
    function mostrarMensagem(lista, texto) {
        lista.innerHTML = "";

        const opcao = document.createElement("option");
        opcao.value = "";
        opcao.textContent = texto;
        opcao.disabled = true;

        lista.appendChild(opcao);
    }


    // filtrar selecao administrativa busca os nomes e controla o aviso de nenhum resultado
    function filtrarOpcoes(campoBusca, lista, mensagem) {

        const pesquisa = normalizarTexto(campoBusca.value);
        let encontrados = 0;

        // comparar opcao administrativa mantem a selecao e oculta nomes fora da busca
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


    // configurar filtro administrativo acompanha digitacao escolha e tecla enter
    function configurarBusca(campoBusca, lista, mensagem) {

        // pesquisar ao digitar atualiza as opcoes do filtro administrativo
        campoBusca.addEventListener("input", function () {
            filtrarOpcoes(campoBusca, lista, mensagem);
        });

        // preservar selecao no filtro refaz a busca quando a escolha muda
        lista.addEventListener("change", function () {
            filtrarOpcoes(campoBusca, lista, mensagem);
        });

        // focar opcoes com enter impede o envio e leva o foco para a lista
        campoBusca.addEventListener("keydown", function (evento) {
            // acessar resultados do cadastro move o foco para a lista ao pressionar enter
            if (evento.key === "Enter") {
                evento.preventDefault();
                lista.focus();
            }
        });
    }


    // buscar professores da turma carrega professores ativos e descarta respostas de turmas anteriores
    async function carregarProfessores(turmaId, carregamentoAtual) {

        professorEvento.disabled = true;
        mostrarMensagem(professorEvento, "Carregando professores...");

        // buscar professores da turma tenta carregar as opcoes para o cadastro do evento
        try {
            const resposta = await fetch("/admin/eventos/professores?turmaId=" + encodeURIComponent(turmaId));

            // conferir busca de professores informa falha quando a consulta nao tem sucesso
            if (!resposta.ok) {
                throw new Error("Erro ao buscar professores");
            }

            const professores = await resposta.json();

            // descartar resposta antiga interrompe a atualizacao quando outra consulta ja foi iniciada
            if (carregamentoAtual !== numeroCarregamento) {
                return;
            }

            professorEvento.innerHTML = "";

            // mostrar turma sem professores avisa quando nao existem professores ativos na turma
            if (!Array.isArray(professores) || professores.length === 0) {
                mostrarMensagem(professorEvento, "Nenhum professor ativo nesta turma");
                return;
            }

            const primeiraOpcao = document.createElement("option");
            primeiraOpcao.value = "";
            primeiraOpcao.textContent = "Selecione um professor";
            professorEvento.appendChild(primeiraOpcao);

            // montar opcao de professor usa o codigo do perfil e o nome recebido
            professores.forEach(function (professor) {

                const opcao = document.createElement("option");
                opcao.value = professor.idPerfil;
                opcao.textContent = professor.nome;

                professorEvento.appendChild(opcao);
            });

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


    // buscar conteudos administrativos carrega os itens da turma e controla a disponibilidade da busca
    async function carregarConteudos(turmaId, carregamentoAtual) {

        conteudosRelacionados.disabled = true;
        buscarConteudo.disabled = true;
        mostrarMensagem(conteudosRelacionados, "Carregando conteúdos...");

        // buscar conteudos para cadastro tenta carregar os materiais da turma escolhida
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

            // mostrar cadastro sem conteudos avisa quando a turma nao possui materiais
            if (!Array.isArray(conteudos) || conteudos.length === 0) {
                mostrarMensagem(conteudosRelacionados, "Nenhum conteúdo cadastrado nesta turma");
                return;
            }

            // montar opcao administrativa de conteudo adiciona o codigo e o titulo a lista
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


    // trocar turma administrativa limpa as selecoes e carrega professores e conteudos da nova turma
    function atualizarTurma() {

        numeroCarregamento++;

        const carregamentoAtual = numeroCarregamento;
        const turmaId = turmaEvento.value;

        professorEvento.disabled = true;
        conteudosRelacionados.disabled = true;

        buscarConteudo.value = "";
        buscarConteudo.disabled = true;
        mensagemBuscaConteudo.hidden = true;

        // exigir turma no cadastro orienta selecionar uma turma antes de buscar professores e conteudos
        if (!turmaId) {
            mostrarMensagem(professorEvento, "Selecione uma turma primeiro");
            mostrarMensagem(conteudosRelacionados, "Selecione uma turma primeiro");
            return;
        }

        carregarProfessores(turmaId, carregamentoAtual);
        carregarConteudos(turmaId, carregamentoAtual);
    }


    // validar datas administrativas exige que o fim nao seja anterior ao inicio
    function validarDatas() {

        const inicio = inicioEvento.value;
        const fim = fimEvento.value;

        fimEvento.min = inicio || "";

        // validar datas do cadastro rejeita termino anterior ao inicio e limpa o erro nos demais casos
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

    // enviar cadastro administrativo valida datas e professor e bloqueia novos envios
    formulario.addEventListener("submit", function (evento) {


        // impedir cadastro repetido cancela o envio quando o evento ja esta sendo cadastrado
        if (enviando) {
            evento.preventDefault();
            return;
        }

        validarDatas();

        // validar formulario antes do envio cancela a solicitacao e mostra os campos invalidos
        if (!formulario.checkValidity()) {
            evento.preventDefault();
            formulario.reportValidity();
            return;
        }

        // exigir professor no cadastro impede o envio e foca o seletor quando nao ha professor
        if (!professorEvento.value) {
            evento.preventDefault();
            professorEvento.focus();
            return;
        }

        // respeitar cadastro cancelado encerra quando outra validacao ja impediu o envio
        if (evento.defaultPrevented) {
            return;
        }

        enviando = true;
        formulario.setAttribute("aria-busy", "true");

        // mostrar cadastro em andamento bloqueia o botao e apresenta o estado de cadastro
        if (botaoCadastrar) {
            botaoCadastrar.disabled = true;
            botaoCadastrar.textContent = "Cadastrando...";
        }
    });


    // restaurar cadastro administrativo libera o formulario e recupera o estado inicial do botao
    window.addEventListener("pageshow", function () {
        enviando = false;
        formulario.removeAttribute("aria-busy");

        // restaurar botao de cadastro recupera o texto e o estado inicial do botao
        if (botaoCadastrar) {
            botaoCadastrar.disabled = botaoDesabilitadoInicialmente;
            botaoCadastrar.textContent = textoOriginalBotao;
        }
    });

    atualizarTurma();
});