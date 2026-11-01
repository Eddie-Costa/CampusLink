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

function converterDataHora(valor) {
    if (!valor) {
        return null;
    }

    return new Date(valor.replace(' ', 'T'));
}

const eventosDados = Array.from(document.querySelectorAll('.evento-dado')).map(function (evento) {
    return {
        nome: evento.dataset.nome,
        inicio: converterDataHora(evento.dataset.inicio),
        fim: converterDataHora(evento.dataset.fim),
        prioridade: evento.dataset.prioridade
    };
});

const disponibilidadesDados = Array.from(document.querySelectorAll('.disponibilidade-dado')).map(function (disponibilidade) {
    return {
        data: disponibilidade.dataset.data,
        horas: Number(disponibilidade.dataset.horas)
    };
});

// permite pesquisar com ou sem acentos
function normalizarTexto(texto) {
    return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

function filtrarOpcoes(campoBusca, lista, mensagem) {
    const pesquisa = normalizarTexto(campoBusca.value);
    let encontrados = 0;

    Array.from(lista.options).forEach(function (opcao) {
        if (!opcao.value) {
            return;
        }

        const corresponde = normalizarTexto(opcao.textContent).includes(pesquisa);
        const mostrar = corresponde || opcao.selected;

        opcao.hidden = !mostrar;
        opcao.style.display = mostrar ? '' : 'none';

        if (corresponde) {
            encontrados++;
        }
    });

    mensagem.hidden = pesquisa === '' || encontrados > 0;
}

function configurarBusca(campoBusca, lista, mensagem) {
    if (!campoBusca || !lista || !mensagem) {
        return;
    }

    campoBusca.addEventListener('input', function () {
        filtrarOpcoes(campoBusca, lista, mensagem);
    });

    lista.addEventListener('change', function () {
        filtrarOpcoes(campoBusca, lista, mensagem);
    });

    campoBusca.addEventListener('keydown', function (evento) {
        if (evento.key === 'Enter') {
            evento.preventDefault();
            lista.focus();
        }
    });
}

function mostrarMensagemConteudos(mensagem) {
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

async function carregarConteudosDaTurma(turmaId) {
    if (!conteudosRelacionados) {
        return;
    }

    numeroCarregamento++;

    const carregamentoAtual = numeroCarregamento;

    conteudosRelacionados.disabled = true;

    if (buscarConteudo) {
        buscarConteudo.value = '';
        buscarConteudo.disabled = true;
    }

    if (mensagemBuscaConteudo) {
        mensagemBuscaConteudo.hidden = true;
    }

    if (!turmaId) {
        mostrarMensagemConteudos('Selecione uma turma primeiro');
        return;
    }

    mostrarMensagemConteudos('Carregando conteúdos...');

    try {
        const resposta = await fetch(`/eventos/conteudos?turmaId=${encodeURIComponent(turmaId)}`);

        if (!resposta.ok) {
            throw new Error(`Erro HTTP ${resposta.status}`);
        }

        const conteudos = await resposta.json();

        // ignora a resposta se o professor ja trocou de turma
        if (carregamentoAtual !== numeroCarregamento) {
            return;
        }

        conteudosRelacionados.innerHTML = '';

        if (!Array.isArray(conteudos) || conteudos.length === 0) {
            mostrarMensagemConteudos('Nenhum conteúdo cadastrado nesta turma');
            conteudosRelacionados.disabled = true;
            return;
        }

        conteudos.forEach(function (conteudo) {
            const option = document.createElement('option');

            option.value = conteudo.id;
            option.textContent = conteudo.titulo;

            conteudosRelacionados.appendChild(option);
        });

        conteudosRelacionados.disabled = false;

        if (buscarConteudo) {
            buscarConteudo.disabled = false;
        }

    } catch (erro) {
        if (carregamentoAtual !== numeroCarregamento) {
            return;
        }

        console.error('Erro ao carregar conteúdos da turma:', erro);

        mostrarMensagemConteudos('Não foi possível carregar os conteúdos');

        conteudosRelacionados.disabled = true;
    }
}

// evita enviar o mesmo cadastro mais de uma vez enquanto carrega
function minutosDoHorario(horario) {
    if (!/^\d{2}:\d{2}$/.test(horario || '')) {
        return null;
    }

    const [horas, minutos] = horario.split(':').map(Number);

    return horas * 60 + minutos;
}

function horarioDosMinutos(totalMinutos) {
    const limitado = Math.max(0, Math.min(totalMinutos, 23 * 60 + 59));

    return `${String(Math.floor(limitado / 60)).padStart(2, '0')}:${String(limitado % 60).padStart(2, '0')}`;
}

function validarHorarioEvento() {
    const inicio = minutosDoHorario(horaInicioEvento.value);
    const fim = minutosDoHorario(horaFimEvento.value);
    let mensagem = '';

    if (inicio !== null && fim !== null && fim <= inicio) {
        mensagem = 'O horário de término deve ser depois do início, no mesmo dia.';
    }

    if (duracaoEvento) {
        duracaoEvento.textContent = inicio !== null && fim !== null && fim > inicio
            ? formatarDuracao(fim - inicio)
            : '';
    }

    horaFimEvento.setCustomValidity(mensagem);

    if (erroHorario) {
        erroHorario.textContent = mensagem;
        erroHorario.hidden = mensagem === '';
    }

    return mensagem === '';
}

function formatarDuracao(minutos) {
    const horas = Math.floor(minutos / 60);
    const resto = minutos % 60;

    if (horas === 0) {
        return `${resto} min`;
    }

    return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
}

// evita enviar o mesmo cadastro mais de uma vez enquanto carrega
function configurarEnvioEvento() {
    if (!formEvento) {
        return;
    }

    const botaoSalvar = document.querySelector('button[type="submit"][form="formEvento"]');
    const conteudoOriginal = botaoSalvar ? botaoSalvar.innerHTML : '';
    let enviando = false;

    formEvento.addEventListener('submit', function (evento) {
        if (enviando || !validarHorarioEvento()) {
            evento.preventDefault();
            return;
        }

        inicioEvento.value = `${dataEvento.value}T${horaInicioEvento.value}`;
        fimEvento.value = `${dataEvento.value}T${horaFimEvento.value}`;

        enviando = true;
        formEvento.setAttribute('aria-busy', 'true');

        if (botaoSalvar) {
            botaoSalvar.disabled = true;
            botaoSalvar.textContent = 'Salvando...';
        }
    });

    // libera o formulario ao voltar pelo navegador
    window.addEventListener('pageshow', function () {
        enviando = false;
        formEvento.removeAttribute('aria-busy');

        if (botaoSalvar) {
            botaoSalvar.disabled = false;
            botaoSalvar.innerHTML = conteudoOriginal;
        }
    });
}

configurarEnvioEvento();
configurarBusca(buscarTurma, turmaEvento, mensagemBuscaTurma);
configurarBusca(buscarConteudo, conteudosRelacionados, mensagemBuscaConteudo);

if (turmaEvento) {
    turmaEvento.addEventListener('change', function () {
        carregarConteudosDaTurma(turmaEvento.value);
    });
}

function dataValida(data) {
    return /^\d{4}-\d{2}-\d{2}$/.test(data || '');
}

function obterDataHoje() {
    const data = new Date();
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, '0');
    const dia = String(data.getDate()).padStart(2, '0');

    return `${ano}-${mes}-${dia}`;
}

function formatarData(data) {
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, '0');
    const dia = String(data.getDate()).padStart(2, '0');

    return `${ano}-${mes}-${dia}`;
}

function criarMarcadorDeEvento(celula, eventosDoDia, dataFormatada) {
    const marcador = document.createElement('span');

    marcador.classList.add('evento-marcador');

    const iniciouHoje = eventosDoDia.some(function (evento) {
        return formatarData(evento.inicio) === dataFormatada;
    });

    const terminouHoje = eventosDoDia.some(function (evento) {
        return formatarData(evento.fim) === dataFormatada;
    });

    if (iniciouHoje && terminouHoje) {
        marcador.classList.add('evento-unico');
    } else if (iniciouHoje) {
        marcador.classList.add('evento-inicio');
    } else if (terminouHoje) {
        marcador.classList.add('evento-fim');
    } else {
        marcador.classList.add('evento-meio');
    }

    const eventosQueComecamHoje = eventosDoDia.filter(function (evento) {
        return formatarData(evento.inicio) === dataFormatada;
    });

    if (eventosQueComecamHoje.length > 1) {
        marcador.textContent = `${eventosQueComecamHoje.length} eventos`;
    } else if (eventosQueComecamHoje.length === 1) {
        marcador.textContent = eventosQueComecamHoje[0].nome;
    } else {
        marcador.textContent = '';
    }

    marcador.title = eventosDoDia.map(function (evento) {
        return evento.nome;
    }).join(', ');

    celula.appendChild(marcador);
}

function criarMarcadorDeDisponibilidade(celula, disponibilidade) {
    const marcador = document.createElement('span');

    marcador.classList.add('disponibilidade-marcador');
    marcador.textContent = `Disponibilidade ${disponibilidade.horas}h`;
    marcador.title = `${disponibilidade.horas} horas disponíveis`;

    celula.classList.add('tem-disponibilidade');

    celula.appendChild(marcador);
}

function atualizarCalendarioDeFundo() {
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

        if (!pertenceAoMesAtual) {
            celula.classList.add('adjacent-month');
        }

        const numeroDia = document.createElement('span');

        numeroDia.classList.add('day-number');
        numeroDia.textContent = String(data.getDate()).padStart(2, '0');

        const simboloMais = document.createElement('span');

        simboloMais.classList.add('day-add');
        simboloMais.textContent = '+';

        const eventosDoDia = eventosDados.filter(function (evento) {
            if (!evento.inicio || !evento.fim) {
                return false;
            }

            const inicio = formatarData(evento.inicio);
            const fim = formatarData(evento.fim);

            return inicio <= dataFormatada && dataFormatada <= fim;
        });

        const disponibilidadeDoDia = disponibilidadesDados.find(function (disponibilidade) {
            return disponibilidade.data === dataFormatada;
        });

        if (!usuarioEhAluno && eventosDoDia.length > 0) {
            celula.classList.add('tem-evento');
            criarMarcadorDeEvento(celula, eventosDoDia, dataFormatada);
        }

        if (usuarioEhAluno && disponibilidadeDoDia) {
            criarMarcadorDeDisponibilidade(celula, disponibilidadeDoDia);
        }

        celula.appendChild(numeroDia);
        celula.appendChild(simboloMais);

        calendarioFundo.appendChild(celula);
    }
}

function eventoEmEdicao() {
    if (!dataEvento || !dataValida(dataEvento.value) || !horaInicioEvento.value || !horaFimEvento.value) {
        return null;
    }

    const inicio = new Date(`${dataEvento.value}T${horaInicioEvento.value}:00`);
    const fim = new Date(`${dataEvento.value}T${horaFimEvento.value}:00`);

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

function atualizarAgendaDoDia() {
    if (!dataEvento) {
        const dataSelecionada = dataValida(queryDate) ? queryDate : obterDataHoje();

        montarAgendaDoDia(gradeHorarios, dataEventosDia, dataSelecionada, eventosDados);
        return;
    }

    const rascunho = eventoEmEdicao();
    const eventos = rascunho ? eventosDados.concat([rascunho]) : eventosDados;

    montarAgendaDoDia(gradeHorarios, dataEventosDia, dataEvento.value, eventos);
}

function rolarAgendaParaRascunho() {
    const inicio = minutosDoHorario(horaInicioEvento.value);

    if (gradeHorarios && inicio !== null) {
        gradeHorarios.scrollTop = Math.max(inicio / 60 * ALTURA_HORA_AGENDA - ALTURA_HORA_AGENDA, 0);
    }
}

function definirInicio(minutos) {
    const duracaoAtual = (minutosDoHorario(horaFimEvento.value) ?? 0) - (minutosDoHorario(horaInicioEvento.value) ?? 0);
    const duracao = duracaoAtual > 0 ? duracaoAtual : 60;

    horaInicioEvento.value = horarioDosMinutos(minutos);
    horaFimEvento.value = horarioDosMinutos(Math.min(minutos + duracao, 23 * 60 + 59));
}

function mudarDia(deslocamento) {
    const data = new Date(`${dataEvento.value}T12:00:00`);

    data.setDate(data.getDate() + deslocamento);
    dataEvento.value = formatarData(data);
    validarHorarioEvento();
    atualizarAgendaDoDia();
}

function configurarHorarioEvento() {
    if (!dataEvento) {
        return;
    }

    const agora = new Date();
    const proximaMeiaHora = Math.min(Math.ceil((agora.getHours() * 60 + agora.getMinutes() + 1) / 30) * 30, 23 * 60);

    dataEvento.value = dataValida(queryDate) ? queryDate : obterDataHoje();
    definirInicio(dataValida(queryDate) ? 8 * 60 : proximaMeiaHora);

    [dataEvento, horaInicioEvento, horaFimEvento].forEach(function (campo) {
        campo.addEventListener('change', function () {
            validarHorarioEvento();
            atualizarAgendaDoDia();
        });
    });

    horaInicioEvento.addEventListener('change', rolarAgendaParaRascunho);

    if (nomeEvento) {
        nomeEvento.addEventListener('input', atualizarAgendaDoDia);
    }

    if (diaAnterior) {
        diaAnterior.addEventListener('click', function () {
            mudarDia(-1);
        });
    }

    if (diaSeguinte) {
        diaSeguinte.addEventListener('click', function () {
            mudarDia(1);
        });
    }

    if (gradeHorarios) {
        gradeHorarios.addEventListener('click', function (evento) {
            const corpo = gradeHorarios.querySelector('.agenda-corpo');

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

if (dataDisponibilidade) {
    dataDisponibilidade.value = dataInicial;
}

if (turmaEvento && turmaEvento.value) {
    carregarConteudosDaTurma(turmaEvento.value);
}

atualizarCalendarioDeFundo();
atualizarAgendaDoDia();
if (dataEvento) {
    rolarAgendaParaRascunho();
}
