// cadastro de eventos conecta turma conteudos horarios e previa na agenda
const parametrosUrl = new URLSearchParams(window.location.search);
const queryDate = parametrosUrl.get('data');

const paginaCadastroEventos = document.getElementById('paginaCadastroEventos');
const tipoUsuario = paginaCadastroEventos ? paginaCadastroEventos.dataset.tipoUsuario : '';
const usuarioEhAluno = tipoUsuario === 'aluno';

const calendarioFundo = document.getElementById('calendarioFundo');
const formEvento = document.getElementById('formEvento');
const inicioEvento = document.getElementById('inicioEvento');
const fimEvento = document.getElementById('fimEvento');
const nomeEvento = document.getElementById('nomeEvento');
const dataEvento = document.getElementById('dataEvento');
const horaInicioEvento = document.getElementById('horaInicioEvento');
const horaFimEvento = document.getElementById('horaFimEvento');
const duracaoEvento = document.getElementById('duracaoEvento');
const erroHorario = document.getElementById('erroHorario');
const diaAnterior = document.getElementById('diaAnterior');
const diaSeguinte = document.getElementById('diaSeguinte');
const dataDisponibilidade = document.getElementById('dataDisponibilidade');
const dataEventosDia = document.getElementById('dataEventosDia');
const gradeHorarios = document.getElementById('gradeHorarios');
const turmaEvento = document.getElementById('turmaEvento');
const conteudosRelacionados = document.getElementById('conteudosRelacionados');
const buscarTurma = document.getElementById('buscarTurma');
const buscarConteudo = document.getElementById('buscarConteudo');
const mensagemBuscaTurma = document.getElementById('mensagemBuscaTurma');
const mensagemBuscaConteudo = document.getElementById('mensagemBuscaConteudo');

let numeroCarregamento = 0;

// converter data do evento transforma o texto recebido em uma data ou retorna vazio
function converterDataHora(valor) {
    // converter data do evento retorna vazio quando nao existe valor
    if (!valor) {
        return null;
    }

    return new Date(valor.replace(' ', 'T'));
}

// ler evento existente extrai nome horarios e prioridade dos dados da pagina
const eventosDados = Array.from(document.querySelectorAll('.evento-dado')).map(function (evento) {
    return {
        nome: evento.dataset.nome,
        inicio: converterDataHora(evento.dataset.inicio),
        fim: converterDataHora(evento.dataset.fim),
        prioridade: evento.dataset.prioridade
    };
});

// ler disponibilidade extrai a data e a quantidade de horas disponiveis
const disponibilidadesDados = Array.from(document.querySelectorAll('.disponibilidade-dado')).map(function (disponibilidade) {
    return {
        data: disponibilidade.dataset.data,
        horas: Number(disponibilidade.dataset.horas)
    };
});


// normalizar filtro de eventos remove acentos espacos externos e letras maiusculas
function normalizarTexto(texto) {
    return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

// filtrar opcoes de evento conta os resultados e controla a mensagem de busca vazia
function filtrarOpcoes(campoBusca, lista, mensagem) {
    const pesquisa = normalizarTexto(campoBusca.value);
    let encontrados = 0;

    // filtrar opcao preserva a selecao e oculta os nomes que nao correspondem a busca
    Array.from(lista.options).forEach(function (opcao) {
        // ignorar opcao sem codigo deixa a mensagem inicial fora da busca
        if (!opcao.value) {
            return;
        }

        const corresponde = normalizarTexto(opcao.textContent).includes(pesquisa);
        const mostrar = corresponde || opcao.selected;

        opcao.hidden = !mostrar;
        opcao.style.display = mostrar ? '' : 'none';

        // contar opcao encontrada aumenta o total quando o texto corresponde a busca
        if (corresponde) {
            encontrados++;
        }
    });

    mensagem.hidden = pesquisa === '' || encontrados > 0;
}

// configurar busca em lista associa digitacao selecao e tecla enter ao filtro
function configurarBusca(campoBusca, lista, mensagem) {
    // configurar filtro de opcoes exige campo lista e mensagem de resultado
    if (!campoBusca || !lista || !mensagem) {
        return;
    }

    // filtrar ao digitar atualiza as opcoes conforme o texto da busca
    campoBusca.addEventListener('input', function () {
        filtrarOpcoes(campoBusca, lista, mensagem);
    });

    // filtrar ao selecionar atualiza as opcoes mantendo o item escolhido visivel
    lista.addEventListener('change', function () {
        filtrarOpcoes(campoBusca, lista, mensagem);
    });

    // focar lista com enter impede o envio do formulario e leva o foco as opcoes
    campoBusca.addEventListener('keydown', function (evento) {
        // acessar lista pelo teclado move o foco para os resultados ao pressionar enter
        if (evento.key === 'Enter') {
            evento.preventDefault();
            lista.focus();
        }
    });
}

// configurar seletor de turma prepara busca selecao e navegacao pelo teclado
function configurarSeletorTurma() {
    const botao = document.getElementById('turmaEventoBotao');
    const rotulo = document.getElementById('turmaEventoRotulo');
    const painel = document.getElementById('turmaEventoPainel');
    const lista = document.getElementById('listaTurmasEvento');

    // configurar seletor de turma exige todos os elementos usados na escolha da turma
    if (!turmaEvento || !botao || !rotulo || !painel || !lista || !buscarTurma) {
        return;
    }

    // listar turmas validas ignora a opcao sem codigo de turma
    const itens = Array.from(turmaEvento.options).filter(function (opcao) {
        return opcao.value;
    // montar opcao de turma cria o item pesquisavel com o nome e o codigo da turma
    }).map(function (opcao) {
        const item = document.createElement('li');
        item.id = `turmaEventoOpcao-${opcao.value}`;
        item.className = 'seletor-turma-opcao';
        item.setAttribute('role', 'option');
        item.dataset.valor = opcao.value;
        item.textContent = opcao.textContent;
        lista.appendChild(item);
        return item;
    });

    // listar turmas visiveis retorna as opcoes que passaram pelo filtro
    function visiveis() {
        // verificar turma visivel aceita apenas as opcoes que nao estao ocultas
        return itens.filter(function (item) {
            return !item.hidden;
        });
    }

    // destacar turma atualiza a opcao ativa e ajusta a rolagem da lista
    function destacar(item) {
        // marcar opcao ativa destaca somente a turma indicada
        itens.forEach(function (outro) {
            outro.classList.toggle('ativa', outro === item);
        });

        // destacar turma na lista identifica e mostra a opcao ativa ou limpa o destaque
        if (item) {
            buscarTurma.setAttribute('aria-activedescendant', item.id);
            item.scrollIntoView({ block: 'nearest' });
        } else {
            buscarTurma.removeAttribute('aria-activedescendant');
        }
    }

    // filtrar seletor de turma oculta nomes diferentes e destaca a primeira opcao encontrada
    function filtrar() {
        const pesquisa = normalizarTexto(buscarTurma.value);

        // comparar nome da turma verifica se o texto normalizado contem a busca
        itens.forEach(function (item) {
            item.hidden = !normalizarTexto(item.textContent).includes(pesquisa);
        });

        mensagemBuscaTurma.hidden = visiveis().length > 0;
        destacar(visiveis()[0] || null);
    }

    // atualizar turma escolhida mostra o nome selecionado e atualiza as opcoes da lista
    function atualizarRotulo() {
        const selecionada = turmaEvento.selectedOptions[0];
        rotulo.textContent = selecionada && selecionada.value ? selecionada.textContent : 'Selecione a turma';
        botao.classList.toggle('vazio', !turmaEvento.value);

        // marcar turma selecionada informa qual opcao corresponde ao valor do formulario
        itens.forEach(function (item) {
            item.setAttribute('aria-selected', String(item.dataset.valor === turmaEvento.value));
        });
    }

    // abrir seletor de turma limpa a busca destaca a selecao e coloca o foco no campo
    function abrir() {
        painel.hidden = false;
        botao.setAttribute('aria-expanded', 'true');
        buscarTurma.value = '';
        filtrar();
        // localizar selecao de turma compara o codigo da opcao com o valor atual
        destacar(itens.find(function (item) {
            return item.dataset.valor === turmaEvento.value;
        }) || visiveis()[0] || null);
        buscarTurma.focus();
    }

    // fechar seletor de turma oculta a lista e pode devolver o foco ao botao
    function fechar(devolverFoco) {
        painel.hidden = true;
        botao.setAttribute('aria-expanded', 'false');

        // devolver foco da turma retorna ao botao depois de fechar o seletor quando solicitado
        if (devolverFoco) {
            botao.focus();
        }
    }

    // escolher turma atualiza o formulario avisa a mudanca e fecha a lista
    function escolher(item) {
        // escolher turma diferente atualiza o valor e avisa a mudanca da selecao
        if (turmaEvento.value !== item.dataset.valor) {
            turmaEvento.value = item.dataset.valor;
            turmaEvento.dispatchEvent(new Event('change'));
        }

        atualizarRotulo();
        fechar(true);
    }

    // alternar seletor de turma abre ou fecha o painel pelo botao
    botao.addEventListener('click', function () {
        // alternar seletor de turma abre o painel oculto ou fecha o painel aberto
        if (painel.hidden) {
            abrir();
        } else {
            fechar(false);
        }
    });

    buscarTurma.addEventListener('input', filtrar);

    // navegar turmas pelo teclado usa setas para destacar enter para escolher e escape para fechar
    buscarTurma.addEventListener('keydown', function (evento) {
        const opcoes = visiveis();
        // localizar turma ativa identifica a opcao destacada na lista
        const atual = opcoes.findIndex(function (item) {
            return item.classList.contains('ativa');
        });

        // navegar nas turmas move o destaque com as setas para cima e para baixo
        if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
            evento.preventDefault();
            const passo = evento.key === 'ArrowDown' ? 1 : -1;
            destacar(opcoes[Math.min(Math.max(atual + passo, 0), opcoes.length - 1)] || null);
        }
        // confirmar turma pelo teclado escolhe a opcao ativa ao pressionar enter
        else if (evento.key === 'Enter') {
            evento.preventDefault();
            // escolher turma ativa confirma a opcao quando existe um item destacado
            if (opcoes[atual]) {
                escolher(opcoes[atual]);
            }
        }
        // fechar seletor com escape fecha o painel e devolve o foco ao botao
        else if (evento.key === 'Escape') {
            evento.preventDefault();
            fechar(true);
        }
    });

    // manter foco na busca impede que o clique pressionado retire o foco do campo
    lista.addEventListener('mousedown', function (evento) {
        evento.preventDefault();
    });

    // escolher turma por clique identifica a opcao clicada e aplica a selecao
    lista.addEventListener('click', function (evento) {
        const item = evento.target.closest('.seletor-turma-opcao');
        // selecionar turma clicada confirma a opcao encontrada no clique
        if (item) {
            escolher(item);
        }
    });

    // fechar seletor por clique oculta o painel quando o clique ocorre fora dele
    document.addEventListener('click', function (evento) {
        // fechar seletor fora do painel oculta a lista ao clicar fora do painel e do botao
        if (!painel.hidden && !painel.contains(evento.target) && !botao.contains(evento.target)) {
            fechar(false);
        }
    });

    // mostrar turma obrigatoria marca o campo invalido e abre a lista para escolha
    turmaEvento.addEventListener('invalid', function () {
        botao.classList.add('invalido');
        abrir();
    });

    // limpar erro de turma remove o aviso visual e atualiza o nome escolhido
    turmaEvento.addEventListener('change', function () {
        botao.classList.remove('invalido');
        atualizarRotulo();
    });

    atualizarRotulo();
}

// mostrar mensagem de conteudos substitui as opcoes por uma mensagem sem selecao
function mostrarMensagemConteudos(mensagem) {
    // mostrar aviso dos conteudos encerra quando o seletor de conteudos nao existe
    if (!conteudosRelacionados) {
        return;
    }

    conteudosRelacionados.innerHTML = '';

    const option = document.createElement('option');
    option.value = '';
    option.textContent = mensagem;
    option.disabled = true;

    conteudosRelacionados.appendChild(option);
}

// carregar conteudos da turma consulta os itens e ignora respostas de selecoes anteriores
async function carregarConteudosDaTurma(turmaId) {
    // carregar conteudos da turma encerra quando a lista de conteudos nao existe
    if (!conteudosRelacionados) {
        return;
    }

    numeroCarregamento++;

    const carregamentoAtual = numeroCarregamento;

    conteudosRelacionados.disabled = true;

    // preparar busca de conteudos limpa e bloqueia o filtro durante o carregamento
    if (buscarConteudo) {
        buscarConteudo.value = '';
        buscarConteudo.disabled = true;
    }

    // limpar aviso de busca oculta a mensagem anterior dos conteudos
    if (mensagemBuscaConteudo) {
        mensagemBuscaConteudo.hidden = true;
    }

    // exigir turma para conteudos pede a escolha de uma turma antes da consulta
    if (!turmaId) {
        mostrarMensagemConteudos('Selecione uma turma primeiro');
        return;
    }

    mostrarMensagemConteudos('Carregando conteúdos...');

    // consultar conteudos da turma tenta carregar e apresentar os materiais disponiveis
    try {
        const resposta = await fetch(`/eventos/conteudos?turmaId=${encodeURIComponent(turmaId)}`);

        // conferir resposta dos conteudos informa o codigo da falha na consulta
        if (!resposta.ok) {
            throw new Error(`Erro HTTP ${resposta.status}`);
        }

        const conteudos = await resposta.json();


        // descartar resposta antiga interrompe a atualizacao quando outra consulta ja foi iniciada
        if (carregamentoAtual !== numeroCarregamento) {
            return;
        }

        conteudosRelacionados.innerHTML = '';

        // mostrar turma sem conteudos avisa quando nao ha uma lista de materiais disponiveis
        if (!Array.isArray(conteudos) || conteudos.length === 0) {
            mostrarMensagemConteudos('Nenhum conteúdo cadastrado nesta turma');
            conteudosRelacionados.disabled = true;
            return;
        }

        // montar opcao de conteudo mostra o titulo e sinaliza a data de prazo encerrado
        conteudos.forEach(function (conteudo) {
            const option = document.createElement('option');

            option.value = conteudo.id;
            option.textContent = conteudo.titulo;
            option.dataset.titulo = conteudo.titulo;

            // destacar conteudo vencido apresenta a data de encerramento na opcao do material
            if (conteudo.prazoEncerradoEm) {
                const [ano, mes, dia] = conteudo.prazoEncerradoEm.slice(0, 10).split('-');

                option.dataset.vencidoEm = `${dia}/${mes}`;
                option.classList.add('conteudo-vencido');
                option.textContent = `${conteudo.titulo}   ·   ⏱ Vencido em ${dia}/${mes}`;
                option.title = `Prazo encerrado em ${dia}/${mes}/${ano}`;
            }

            conteudosRelacionados.appendChild(option);
        });

        conteudosRelacionados.disabled = false;

        // liberar busca de conteudos habilita o filtro depois do carregamento
        if (buscarConteudo) {
            buscarConteudo.disabled = false;
        }

    } catch (erro) {
        // descartar resposta antiga interrompe a atualizacao quando outra consulta ja foi iniciada
        if (carregamentoAtual !== numeroCarregamento) {
            return;
        }

        console.error('Erro ao carregar conteúdos da turma:', erro);

        mostrarMensagemConteudos('Não foi possível carregar os conteúdos');

        conteudosRelacionados.disabled = true;
    }
}


// converter horario em minutos valida o formato e soma horas e minutos
function minutosDoHorario(horario) {
    // converter horario em minutos rejeita valores fora do formato de horas e minutos
    if (!/^\d{2}:\d{2}$/.test(horario || '')) {
        return null;
    }

    const [horas, minutos] = horario.split(':').map(Number);

    return horas * 60 + minutos;
}

// converter minutos em horario limita o valor ao dia e monta horas e minutos
function horarioDosMinutos(totalMinutos) {
    const limitado = Math.max(0, Math.min(totalMinutos, 23 * 60 + 59));

    return `${String(Math.floor(limitado / 60)).padStart(2, '0')}:${String(limitado % 60).padStart(2, '0')}`;
}

// validar horario do evento exige termino depois do inicio e mostra a duracao
function validarHorarioEvento() {
    const inicio = minutosDoHorario(horaInicioEvento.value);
    const fim = minutosDoHorario(horaFimEvento.value);
    let mensagem = '';

    // validar horario do formulario exige termino posterior ao inicio no mesmo dia
    if (inicio !== null && fim !== null && fim <= inicio) {
        mensagem = 'O horário de término deve ser depois do início, no mesmo dia.';
    }

    // mostrar duracao do evento preenche a duracao apenas para horarios validos
    if (duracaoEvento) {
        duracaoEvento.textContent = inicio !== null && fim !== null && fim > inicio
            ? formatarDuracao(fim - inicio)
            : '';
    }

    horaFimEvento.setCustomValidity(mensagem);

    // mostrar erro de horario atualiza o aviso conforme o resultado da validacao
    if (erroHorario) {
        erroHorario.textContent = mensagem;
        erroHorario.hidden = mensagem === '';
    }

    return mensagem === '';
}

// formatar duracao do evento apresenta o tempo em horas e minutos
function formatarDuracao(minutos) {
    const horas = Math.floor(minutos / 60);
    const resto = minutos % 60;

    // formatar duracao curta mostra apenas minutos quando nao completa uma hora
    if (horas === 0) {
        return `${resto} min`;
    }

    return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
}


// configurar envio do evento prepara validacao confirmacao de vencidos e bloqueio de envio repetido
function configurarEnvioEvento() {
    // configurar envio do evento encerra quando o formulario nao existe
    if (!formEvento) {
        return;
    }

    const botaoSalvar = document.querySelector('button[type="submit"][form="formEvento"]');
    const conteudoOriginal = botaoSalvar ? botaoSalvar.innerHTML : '';
    let enviando = false;

    const avisoVencidos = document.getElementById('avisoConteudosVencidos');
    const listaVencidos = document.getElementById('listaConteudosVencidos');
    const confirmarCriacao = document.getElementById('confirmarCriacaoEvento');
    let vencidosConfirmados = false;

    // confirmar conteudos vencidos configura o botao que aceita os materiais e reenvia o formulario
    if (confirmarCriacao) {
        // confirmar conteudos vencidos fecha o aviso e tenta enviar o evento novamente
        confirmarCriacao.addEventListener('click', function () {
            vencidosConfirmados = true;
            bootstrap.Modal.getOrCreateInstance(avisoVencidos).hide();
            formEvento.requestSubmit();
        });
    }

    // enviar evento valida os horarios confirma vencidos e bloqueia o botao durante o envio
    formEvento.addEventListener('submit', function (evento) {
        // bloquear envio do evento impede envio repetido ou com horario invalido
        if (enviando || !validarHorarioEvento()) {
            evento.preventDefault();
            return;
        }

        const vencidosSelecionados = conteudosRelacionados
            // identificar conteudo vencido seleciona as opcoes com prazo encerrado
            ? Array.from(conteudosRelacionados.selectedOptions).filter(function (opcao) {
                return opcao.dataset.vencidoEm;
            })
            : [];

        // avisar conteudos vencidos exige confirmacao antes de cadastrar um evento com materiais vencidos
        if (vencidosSelecionados.length > 0 && !vencidosConfirmados && avisoVencidos && typeof bootstrap !== 'undefined') {
            evento.preventDefault();
            listaVencidos.innerHTML = '';

            // listar vencido na confirmacao mostra o titulo do conteudo e a data de vencimento
            vencidosSelecionados.forEach(function (opcao) {
                const item = document.createElement('li');
                const titulo = document.createElement('span');
                const tag = document.createElement('span');

                titulo.textContent = opcao.dataset.titulo;
                tag.classList.add('tag-vencido');
                tag.textContent = `Vencido em ${opcao.dataset.vencidoEm}`;

                item.append(titulo, tag);
                listaVencidos.appendChild(item);
            });

            bootstrap.Modal.getOrCreateInstance(avisoVencidos).show();
            return;
        }

        inicioEvento.value = `${dataEvento.value}T${horaInicioEvento.value}`;
        fimEvento.value = `${dataEvento.value}T${horaFimEvento.value}`;

        enviando = true;
        formEvento.setAttribute('aria-busy', 'true');

        // mostrar evento salvando bloqueia o botao enquanto o formulario e enviado
        if (botaoSalvar) {
            botaoSalvar.disabled = true;
            botaoSalvar.textContent = 'Salvando...';
        }
    });


    // liberar cadastro ao voltar restaura o formulario e o botao quando a pagina reaparece
    window.addEventListener('pageshow', function () {
        enviando = false;
        formEvento.removeAttribute('aria-busy');

        // restaurar botao do evento libera o botao e recupera seu texto original
        if (botaoSalvar) {
            botaoSalvar.disabled = false;
            botaoSalvar.innerHTML = conteudoOriginal;
        }
    });
}

configurarEnvioEvento();
configurarSeletorTurma();
configurarBusca(buscarConteudo, conteudosRelacionados, mensagemBuscaConteudo);

// atualizar conteudos por turma configura a consulta ao trocar a turma selecionada
if (turmaEvento) {
    // atualizar conteudos selecionados carrega os itens da nova turma
    turmaEvento.addEventListener('change', function () {
        carregarConteudosDaTurma(turmaEvento.value);
    });
}

// validar formato da data confere o padrao de ano mes e dia
function dataValida(data) {
    return /^\d{4}-\d{2}-\d{2}$/.test(data || '');
}

// obter data de hoje monta a data atual com o horario local
function obterDataHoje() {
    const data = new Date();
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, '0');
    const dia = String(data.getDate()).padStart(2, '0');

    return `${ano}-${mes}-${dia}`;
}

// formatar data do calendario monta ano mes e dia com dois digitos
function formatarData(data) {
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, '0');
    const dia = String(data.getDate()).padStart(2, '0');

    return `${ano}-${mes}-${dia}`;
}

// marcar eventos do calendario distingue inicio meio e fim e mostra os nomes do dia
function criarMarcadorDeEvento(celula, eventosDoDia, dataFormatada) {
    const marcador = document.createElement('span');

    marcador.classList.add('evento-marcador');

    // verificar inicio no dia identifica eventos que comecam na data do marcador
    const iniciouHoje = eventosDoDia.some(function (evento) {
        return formatarData(evento.inicio) === dataFormatada;
    });

    // verificar fim no dia identifica eventos que terminam na data do marcador
    const terminouHoje = eventosDoDia.some(function (evento) {
        return formatarData(evento.fim) === dataFormatada;
    });

    // marcar evento de um dia usa o estilo de evento que comeca e termina na mesma data
    if (iniciouHoje && terminouHoje) {
        marcador.classList.add('evento-unico');
    }
    // marcar inicio do evento aplica o estilo do primeiro dia
    else if (iniciouHoje) {
        marcador.classList.add('evento-inicio');
    }
    // marcar fim do evento distingue o ultimo dia dos dias intermediarios
    else if (terminouHoje) {
        marcador.classList.add('evento-fim');
    } else {
        marcador.classList.add('evento-meio');
    }

    // listar inicios do dia seleciona os eventos que comecam na data exibida
    const eventosQueComecamHoje = eventosDoDia.filter(function (evento) {
        return formatarData(evento.inicio) === dataFormatada;
    });

    // resumir varios eventos mostra a quantidade de eventos que comecam no dia
    if (eventosQueComecamHoje.length > 1) {
        marcador.textContent = `${eventosQueComecamHoje.length} eventos`;
    }
    // mostrar unico evento usa o nome quando apenas um evento comeca no dia
    else if (eventosQueComecamHoje.length === 1) {
        marcador.textContent = eventosQueComecamHoje[0].nome;
    } else {
        marcador.textContent = '';
    }

    // listar nomes do marcador retorna o nome usado na descricao dos eventos
    marcador.title = eventosDoDia.map(function (evento) {
        return evento.nome;
    }).join(', ');

    celula.appendChild(marcador);
}

// marcar disponibilidade mostra a quantidade de horas disponiveis na celula
function criarMarcadorDeDisponibilidade(celula, disponibilidade) {
    const marcador = document.createElement('span');

    marcador.classList.add('disponibilidade-marcador');
    marcador.textContent = `Disponibilidade ${disponibilidade.horas}h`;
    marcador.title = `${disponibilidade.horas} horas disponíveis`;

    celula.classList.add('tem-disponibilidade');

    celula.appendChild(marcador);
}

// montar calendario de fundo desenha o mes e os marcadores de eventos ou disponibilidade
function atualizarCalendarioDeFundo() {
    // atualizar calendario de fundo encerra quando o calendario nao existe
    if (!calendarioFundo) {
        return;
    }

    calendarioFundo.innerHTML = '';

    const dataReferencia = dataValida(queryDate) ? new Date(`${queryDate}T12:00:00`) : new Date();
    const mes = dataReferencia.getMonth();
    const ano = dataReferencia.getFullYear();
    const primeiroDiaSemana = new Date(ano, mes, 1).getDay();
    const ultimoDiaMes = new Date(ano, mes + 1, 0).getDate();
    const quantidadeCelulas = Math.ceil((primeiroDiaSemana + ultimoDiaMes) / 7) * 7;

    for (let indice = 0; indice < quantidadeCelulas; indice++) {
        const data = new Date(ano, mes, indice - primeiroDiaSemana + 1);
        const dataFormatada = formatarData(data);
        const pertenceAoMesAtual = data.getMonth() === mes && data.getFullYear() === ano;

        const celula = document.createElement('div');

        celula.classList.add('day-cell');
        celula.dataset.date = dataFormatada;

        // marcar mes vizinho aplica o estilo de data fora do mes atual
        if (!pertenceAoMesAtual) {
            celula.classList.add('adjacent-month');
        }

        const numeroDia = document.createElement('span');

        numeroDia.classList.add('day-number');
        numeroDia.textContent = String(data.getDate()).padStart(2, '0');

        const simboloMais = document.createElement('span');

        simboloMais.classList.add('day-add');
        simboloMais.textContent = '+';

        // filtrar evento na celula confere se a data esta entre o inicio e o fim
        const eventosDoDia = eventosDados.filter(function (evento) {
            // filtrar eventos sem horario ignora eventos sem inicio ou fim
            if (!evento.inicio || !evento.fim) {
                return false;
            }

            const inicio = formatarData(evento.inicio);
            const fim = formatarData(evento.fim);

            return inicio <= dataFormatada && dataFormatada <= fim;
        });

        // buscar disponibilidade da celula compara a data disponivel com a data exibida
        const disponibilidadeDoDia = disponibilidadesDados.find(function (disponibilidade) {
            return disponibilidade.data === dataFormatada;
        });

        // marcar eventos no cadastro mostra os eventos do dia para usuarios que nao sao alunos
        if (!usuarioEhAluno && eventosDoDia.length > 0) {
            celula.classList.add('tem-evento');
            criarMarcadorDeEvento(celula, eventosDoDia, dataFormatada);
        }

        // marcar disponibilidade do aluno mostra as horas cadastradas para o dia
        if (usuarioEhAluno && disponibilidadeDoDia) {
            criarMarcadorDeDisponibilidade(celula, disponibilidadeDoDia);
        }

        celula.appendChild(numeroDia);
        celula.appendChild(simboloMais);

        calendarioFundo.appendChild(celula);
    }
}

// montar previa do evento usa o nome e os horarios validos do formulario
function eventoEmEdicao() {
    // montar rascunho do evento exige uma data valida e os dois horarios
    if (!dataEvento || !dataValida(dataEvento.value) || !horaInicioEvento.value || !horaFimEvento.value) {
        return null;
    }

    const inicio = new Date(`${dataEvento.value}T${horaInicioEvento.value}:00`);
    const fim = new Date(`${dataEvento.value}T${horaFimEvento.value}:00`);

    // validar rascunho do evento ignora um periodo com termino anterior ou igual ao inicio
    if (fim <= inicio) {
        return null;
    }

    return {
        nome: 'Prévia · ' + (nomeEvento && nomeEvento.value.trim() ? nomeEvento.value.trim() : 'Novo evento'),
        inicio: inicio,
        fim: fim,
        prioridade: 'rascunho'
    };
}

// atualizar agenda de cadastro inclui a previa do evento junto aos eventos existentes
function atualizarAgendaDoDia() {
    // mostrar agenda sem formulario usa a data selecionada ou a data atual
    if (!dataEvento) {
        const dataSelecionada = dataValida(queryDate) ? queryDate : obterDataHoje();

        montarAgendaDoDia(gradeHorarios, dataEventosDia, dataSelecionada, eventosDados);
        return;
    }

    const rascunho = eventoEmEdicao();
    const eventos = rascunho ? eventosDados.concat([rascunho]) : eventosDados;

    montarAgendaDoDia(gradeHorarios, dataEventosDia, dataEvento.value, eventos);
}

// rolar ate a previa posiciona a agenda perto do horario escolhido
function rolarAgendaParaRascunho() {
    const inicio = minutosDoHorario(horaInicioEvento.value);

    // localizar rascunho na agenda rola a grade ate o horario de inicio informado
    if (gradeHorarios && inicio !== null) {
        gradeHorarios.scrollTop = Math.max(inicio / 60 * ALTURA_HORA_AGENDA - ALTURA_HORA_AGENDA, 0);
    }
}

// definir inicio do evento ajusta os horarios preservando a duracao quando possivel
function definirInicio(minutos) {
    const duracaoAtual = (minutosDoHorario(horaFimEvento.value) ?? 0) - (minutosDoHorario(horaInicioEvento.value) ?? 0);
    const duracao = duracaoAtual > 0 ? duracaoAtual : 60;

    horaInicioEvento.value = horarioDosMinutos(minutos);
    horaFimEvento.value = horarioDosMinutos(Math.min(minutos + duracao, 23 * 60 + 59));
}

// mudar dia do evento altera a data e atualiza a validacao e a agenda
function mudarDia(deslocamento) {
    const data = new Date(`${dataEvento.value}T12:00:00`);

    data.setDate(data.getDate() + deslocamento);
    dataEvento.value = formatarData(data);
    validarHorarioEvento();
    atualizarAgendaDoDia();
}

// configurar horario de cadastro define valores iniciais e conecta campos e cliques a previa
function configurarHorarioEvento() {
    // configurar horario do evento encerra quando o campo de data nao existe
    if (!dataEvento) {
        return;
    }

    const agora = new Date();
    const proximaMeiaHora = Math.min(Math.ceil((agora.getHours() * 60 + agora.getMinutes() + 1) / 30) * 30, 23 * 60);

    dataEvento.value = dataValida(queryDate) ? queryDate : obterDataHoje();
    definirInicio(dataValida(queryDate) ? 8 * 60 : proximaMeiaHora);

    // acompanhar campo de horario associa a mudanca do campo a atualizacao da agenda
    [dataEvento, horaInicioEvento, horaFimEvento].forEach(function (campo) {
        // atualizar previa por horario valida o intervalo e redesenha os eventos do dia
        campo.addEventListener('change', function () {
            validarHorarioEvento();
            atualizarAgendaDoDia();
        });
    });

    horaInicioEvento.addEventListener('change', rolarAgendaParaRascunho);

    // atualizar nome na agenda acompanha as alteracoes do nome do evento
    if (nomeEvento) {
        nomeEvento.addEventListener('input', atualizarAgendaDoDia);
    }

    // configurar dia anterior habilita o botao que recua um dia
    if (diaAnterior) {
        // escolher dia anterior volta uma data no cadastro do evento
        diaAnterior.addEventListener('click', function () {
            mudarDia(-1);
        });
    }

    // configurar dia seguinte habilita o botao que avanca um dia
    if (diaSeguinte) {
        // escolher dia seguinte avanca uma data no cadastro do evento
        diaSeguinte.addEventListener('click', function () {
            mudarDia(1);
        });
    }

    // escolher horario na agenda configura o clique na grade para definir o inicio
    if (gradeHorarios) {
        // escolher horario na agenda converte a posicao do clique em intervalos de meia hora
        gradeHorarios.addEventListener('click', function (evento) {
            const corpo = gradeHorarios.querySelector('.agenda-corpo');

            // localizar grade da agenda ignora cliques quando o corpo da grade nao existe
            if (!corpo) {
                return;
            }

            const deslocamento = evento.clientY - corpo.getBoundingClientRect().top;
            const minutos = Math.floor(deslocamento / ALTURA_HORA_AGENDA * 60 / 30) * 30;

            definirInicio(Math.max(0, Math.min(minutos, 23 * 60)));
            validarHorarioEvento();
            atualizarAgendaDoDia();
        });
    }

    validarHorarioEvento();
}

const dataInicial = dataValida(queryDate) ? queryDate : obterDataHoje();

configurarHorarioEvento();

// preencher data da disponibilidade usa a data inicial no campo disponivel
if (dataDisponibilidade) {
    dataDisponibilidade.value = dataInicial;
}

// carregar turma preselecionada consulta os conteudos quando o formulario ja tem uma turma
if (turmaEvento && turmaEvento.value) {
    carregarConteudosDaTurma(turmaEvento.value);
}

atualizarCalendarioDeFundo();
atualizarAgendaDoDia();
// posicionar rascunho inicial rola a agenda quando existe o campo de data do evento
if (dataEvento) {
    rolarAgendaParaRascunho();
}
