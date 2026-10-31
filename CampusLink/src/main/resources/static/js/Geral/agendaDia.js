const ALTURA_HORA_AGENDA = 44;
const MINUTOS_DIA = 24 * 60;

function formatarDataExibicao(data) {
    const dataFormatada = new Date(`${data}T12:00:00`);

    return dataFormatada.toLocaleDateString('pt-BR', {
        weekday: 'long',
        day: '2-digit',
        month: 'long',
        year: 'numeric'
    });
}

function formatarHoraAgenda(data) {
    return data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function descreverHorarioAgenda(evento) {
    if (evento.inicio.toDateString() === evento.fim.toDateString()) {
        return `${formatarHoraAgenda(evento.inicio)} – ${formatarHoraAgenda(evento.fim)}`;
    }

    const dataCurta = { day: '2-digit', month: '2-digit' };

    return `${evento.inicio.toLocaleDateString('pt-BR', dataCurta)} ${formatarHoraAgenda(evento.inicio)} – ` +
        `${evento.fim.toLocaleDateString('pt-BR', dataCurta)} ${formatarHoraAgenda(evento.fim)}`;
}

function obterSegmentosDoDia(eventos, data) {
    const inicioDia = new Date(`${data}T00:00:00`);
    const fimDia = new Date(inicioDia.getTime() + MINUTOS_DIA * 60 * 1000);

    return eventos
        .filter(function (evento) {
            return evento.inicio && evento.fim && evento.inicio < fimDia && evento.fim > inicioDia;
        })
        .map(function (evento) {
            return {
                evento: evento,
                inicio: Math.max(0, (evento.inicio - inicioDia) / 60000),
                fim: Math.min(MINUTOS_DIA, (evento.fim - inicioDia) / 60000)
            };
        })
        .sort(function (segmentoA, segmentoB) {
            return segmentoA.inicio - segmentoB.inicio || segmentoB.fim - segmentoA.fim;
        });
}

function distribuirEmColunas(segmentos) {
    let grupo = [];
    let fimDasColunas = [];
    let fimDoGrupo = -1;

    function fecharGrupo() {
        grupo.forEach(function (segmento) {
            segmento.totalColunas = fimDasColunas.length;
        });
        grupo = [];
        fimDasColunas = [];
    }

    segmentos.forEach(function (segmento) {
        if (grupo.length > 0 && segmento.inicio >= fimDoGrupo) {
            fecharGrupo();
        }

        let coluna = fimDasColunas.findIndex(function (fimColuna) {
            return fimColuna <= segmento.inicio;
        });

        if (coluna === -1) {
            coluna = fimDasColunas.length;
            fimDasColunas.push(segmento.fim);
        } else {
            fimDasColunas[coluna] = segmento.fim;
        }

        segmento.coluna = coluna;
        grupo.push(segmento);
        fimDoGrupo = Math.max(fimDoGrupo, segmento.fim);
    });

    fecharGrupo();
}

function criarCardEvento(segmento) {
    const evento = segmento.evento;
    const card = document.createElement('div');
    const nome = document.createElement('strong');
    const horario = document.createElement('span');
    const largura = 100 / segmento.totalColunas;

    card.classList.add('agenda-evento', `agenda-prioridade-${(evento.prioridade || 'MEDIA').toLowerCase()}`);
    card.style.top = `${segmento.inicio / 60 * ALTURA_HORA_AGENDA}px`;
    card.style.height = `${Math.max((segmento.fim - segmento.inicio) / 60 * ALTURA_HORA_AGENDA, 24)}px`;
    card.style.left = `calc(${segmento.coluna * largura}% + 2px)`;
    card.style.width = `calc(${largura}% - 4px)`;

    nome.textContent = evento.nome;
    horario.textContent = descreverHorarioAgenda(evento);
    card.title = `${evento.nome} · ${horario.textContent}`;

    card.append(nome, horario);

    return card;
}

function montarAgendaDoDia(gradeHorarios, dataEventosDia, dataSelecionada, eventos) {
    if (!gradeHorarios || !dataEventosDia) {
        return 0;
    }

    dataEventosDia.textContent = formatarDataExibicao(dataSelecionada);
    gradeHorarios.innerHTML = '';

    const corpo = document.createElement('div');

    corpo.classList.add('agenda-corpo');
    corpo.style.height = `${24 * ALTURA_HORA_AGENDA}px`;

    for (let hora = 0; hora < 24; hora++) {
        const linha = document.createElement('div');
        const horario = document.createElement('time');

        linha.classList.add('horario-linha');
        linha.style.top = `${hora * ALTURA_HORA_AGENDA}px`;

        horario.classList.add('horario-hora');
        horario.textContent = `${String(hora).padStart(2, '0')}:00`;

        linha.appendChild(horario);
        corpo.appendChild(linha);
    }

    const areaEventos = document.createElement('div');

    areaEventos.classList.add('agenda-eventos');

    const segmentos = obterSegmentosDoDia(eventos, dataSelecionada);

    distribuirEmColunas(segmentos);
    segmentos.forEach(function (segmento) {
        areaEventos.appendChild(criarCardEvento(segmento));
    });

    corpo.appendChild(areaEventos);
    gradeHorarios.appendChild(corpo);

    return segmentos.length > 0 ? segmentos[0].inicio / 60 * ALTURA_HORA_AGENDA : 0;
}
