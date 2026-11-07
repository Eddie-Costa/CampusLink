// agenda diaria organiza os eventos por horario e separa compromissos simultaneos
const ALTURA_HORA_AGENDA = 44;
const MINUTOS_DIA = 24 * 60;

// formatar data da agenda mostra o dia da semana e a data por extenso
function formatarDataExibicao(data) {
    const dataFormatada = new Date(`${data}T12:00:00`);

    return dataFormatada.toLocaleDateString('pt-BR', {
        weekday: 'long',
        day: '2-digit',
        month: 'long',
        year: 'numeric'
    });
}

// formatar hora da agenda mostra horas e minutos no formato brasileiro
function formatarHoraAgenda(data) {
    return data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

// descrever horario do evento inclui as datas quando o evento atravessa o dia
function descreverHorarioAgenda(evento) {
    // mostrar horario da agenda exibe apenas os horarios quando o evento ocorre no mesmo dia
    if (evento.inicio.toDateString() === evento.fim.toDateString()) {
        return `${formatarHoraAgenda(evento.inicio)} – ${formatarHoraAgenda(evento.fim)}`;
    }

    const dataCurta = { day: '2-digit', month: '2-digit' };

    return `${evento.inicio.toLocaleDateString('pt-BR', dataCurta)} ${formatarHoraAgenda(evento.inicio)} – ` +
        `${evento.fim.toLocaleDateString('pt-BR', dataCurta)} ${formatarHoraAgenda(evento.fim)}`;
}

// listar eventos do dia recorta os horarios e ordena os eventos que ocupam a data escolhida
function obterSegmentosDoDia(eventos, data) {
    const inicioDia = new Date(`${data}T00:00:00`);
    const fimDia = new Date(inicioDia.getTime() + MINUTOS_DIA * 60 * 1000);

    return eventos
        // filtrar eventos do dia confere se o intervalo do evento cruza a data escolhida
        .filter(function (evento) {
            return evento.inicio && evento.fim && evento.inicio < fimDia && evento.fim > inicioDia;
        })
        // recortar horario do evento limita o inicio e o fim aos minutos do dia
        .map(function (evento) {
            return {
                evento: evento,
                inicio: Math.max(0, (evento.inicio - inicioDia) / 60000),
                fim: Math.min(MINUTOS_DIA, (evento.fim - inicioDia) / 60000)
            };
        })
        // ordenar agenda compara o inicio dos eventos e desempata pela maior duracao
        .sort(function (segmentoA, segmentoB) {
            return segmentoA.inicio - segmentoB.inicio || segmentoB.fim - segmentoA.fim;
        });
}

// distribuir agenda separa eventos simultaneos em colunas
function distribuirEmColunas(segmentos) {
    let grupo = [];
    let fimDasColunas = [];
    let fimDoGrupo = -1;

    // fechar grupo da agenda registra as colunas ocupadas e prepara o proximo grupo
    function fecharGrupo() {
        // contar colunas do grupo informa quantas colunas cada evento deve dividir
        grupo.forEach(function (segmento) {
            segmento.totalColunas = fimDasColunas.length;
        });
        grupo = [];
        fimDasColunas = [];
    }

    // posicionar evento procura uma coluna livre e atualiza o grupo de horarios
    segmentos.forEach(function (segmento) {
        // separar grupos da agenda fecha o grupo quando o proximo evento nao coincide com ele
        if (grupo.length > 0 && segmento.inicio >= fimDoGrupo) {
            fecharGrupo();
        }

        // buscar coluna livre verifica se o evento anterior terminou antes do proximo
        let coluna = fimDasColunas.findIndex(function (fimColuna) {
            return fimColuna <= segmento.inicio;
        });

        // organizar colunas da agenda cria uma coluna quando nenhuma existente esta livre
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

// montar cartao da agenda define tamanho posicao prioridade e acesso aos detalhes do evento
function criarCardEvento(segmento) {
    const evento = segmento.evento;
    const card = document.createElement('div');
    const nome = document.createElement('strong');
    const horario = document.createElement('span');
    const largura = 100 / segmento.totalColunas;
    const altura = Math.max((segmento.fim - segmento.inicio) / 60 * ALTURA_HORA_AGENDA, 24);

    card.classList.add('agenda-evento', `agenda-prioridade-${(evento.prioridade || 'MEDIA').toLowerCase()}`);
    card.classList.toggle('agenda-evento-curto', altura < 38);
    card.style.top = `${segmento.inicio / 60 * ALTURA_HORA_AGENDA}px`;
    card.style.height = `${altura}px`;
    card.style.left = `calc(${segmento.coluna * largura}% + 2px)`;
    card.style.width = `calc(${largura}% - 4px)`;

    nome.textContent = evento.nome;
    horario.textContent = descreverHorarioAgenda(evento);
    card.title = `${evento.nome} · ${horario.textContent}`;

    // abrir evento pela agenda torna o cartao acessivel e clicavel quando existe um codigo
    if (evento.id) {
        card.classList.add('agenda-evento-clicavel');
        card.dataset.idEvento = evento.id;
        card.tabIndex = 0;
        card.setAttribute('role', 'button');
        card.setAttribute('aria-label', `Ver detalhes de ${evento.nome}, ${horario.textContent}`);
    }

    card.append(nome, horario);

    return card;
}

// montar agenda do dia desenha as horas e os eventos e retorna a primeira posicao ocupada
function montarAgendaDoDia(gradeHorarios, dataEventosDia, dataSelecionada, eventos) {
    // montar agenda diaria encerra quando faltam a grade ou os dados dos eventos
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
    // adicionar cartao da agenda coloca o evento na area de horarios
    segmentos.forEach(function (segmento) {
        areaEventos.appendChild(criarCardEvento(segmento));
    });

    corpo.appendChild(areaEventos);
    gradeHorarios.appendChild(corpo);

    return segmentos.length > 0 ? segmentos[0].inicio / 60 * ALTURA_HORA_AGENDA : 0;
}
