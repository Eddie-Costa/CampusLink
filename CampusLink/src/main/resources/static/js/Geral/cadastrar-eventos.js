const parametrosUrl = new URLSearchParams(window.location.search);
const queryDate = parametrosUrl.get('data');

const paginaCadastroEventos = document.getElementById('paginaCadastroEventos');
const tipoUsuario = paginaCadastroEventos ? paginaCadastroEventos.dataset.tipoUsuario : '';
const usuarioEhAluno = tipoUsuario === 'aluno';

const calendarioFundo = document.getElementById('calendarioFundo');
const startInput = document.getElementById('inicioEvento');
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

function formatarDataExibicao(data) {
    const dataFormatada = new Date(`${data}T12:00:00`);

    return dataFormatada.toLocaleDateString('pt-BR', {
        weekday: 'long',
        day: '2-digit',
        month: 'long',
        year: 'numeric'
    });
}

function obterEventosDaHora(data, hora) {
    const inicioHora = new Date(`${data}T${String(hora).padStart(2, '0')}:00:00`);
    const fimHora = new Date(inicioHora.getTime() + 60 * 60 * 1000);

    return eventosDados.filter(function (evento) {
        return evento.inicio &&
            evento.fim &&
            evento.inicio < fimHora &&
            evento.fim > inicioHora;
    });
}

function atualizarAgendaDoDia() {
    if (!gradeHorarios || !dataEventosDia) {
        return;
    }

    const dataSelecionada = dataValida(queryDate) ? queryDate : obterDataHoje();

    dataEventosDia.textContent = formatarDataExibicao(dataSelecionada);
    gradeHorarios.innerHTML = '';

    for (let hora = 0; hora < 24; hora++) {
        const eventosDaHora = obterEventosDaHora(dataSelecionada, hora);

        const linha = document.createElement('div');

        linha.classList.add('horario-linha');

        const horario = document.createElement('time');

        horario.classList.add('horario-hora');
        horario.textContent = `${String(hora).padStart(2, '0')}:00`;

        const faixa = document.createElement('div');

        faixa.classList.add('horario-faixa');

        if (eventosDaHora.length === 0) {
            faixa.classList.add('horario-livre');

            const linhaLivre = document.createElement('span');

            linhaLivre.classList.add('linha-livre');

            faixa.appendChild(linhaLivre);
        } else {
            faixa.classList.add('horario-ocupado');

            const nomes = eventosDaHora.map(function (evento) {
                return evento.nome;
            }).join(', ');

            const eventoTexto = document.createElement('span');

            eventoTexto.classList.add('nome-evento-horario');

            if (eventosDaHora.length === 1) {
                eventoTexto.textContent = nomes;
            } else {
                eventoTexto.textContent = `${eventosDaHora.length} eventos: ${nomes}`;
            }

            faixa.appendChild(eventoTexto);
        }

        linha.appendChild(horario);
        linha.appendChild(faixa);

        gradeHorarios.appendChild(linha);
    }
}

const dataInicial = dataValida(queryDate) ? queryDate : obterDataHoje();

if (startInput && queryDate) {
    startInput.value = queryDate + 'T08:00';
}

if (dataDisponibilidade) {
    dataDisponibilidade.value = dataInicial;
}

if (turmaEvento && turmaEvento.value) {
    carregarConteudosDaTurma(turmaEvento.value);
}

atualizarCalendarioDeFundo();
atualizarAgendaDoDia();