document.addEventListener('DOMContentLoaded', function () {
    const meses = [
        'Janeiro', 'Fevereiro', 'Março', 'Abril',
        'Maio', 'Junho', 'Julho', 'Agosto',
        'Setembro', 'Outubro', 'Novembro', 'Dezembro'
    ];

    const diasSemana = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];
    const nomesDiasSemana = [
        'Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira',
        'Quinta-feira', 'Sexta-feira', 'Sábado'
    ];

    const pagina = document.querySelector('.page-content');
    const tipoUsuario = pagina ? pagina.dataset.tipoUsuario || '' : '';
    const usuarioEhAluno = tipoUsuario.toLowerCase() === 'aluno';
    const usuarioEhProfessor = tipoUsuario.toLowerCase() === 'professor';
    const urlCadastro = pagina ? pagina.dataset.urlCadastro || '/eventos/cadastrar' : '/eventos/cadastrar';

    const eventosDados = Array.from(document.querySelectorAll('.evento-dado')).map(function (evento) {
        return {
            dataInicio: evento.dataset.inicio || '',
            dataFim: evento.dataset.fim || '',
            nome: evento.dataset.nome || '',
            prioridade: evento.dataset.prioridade || 'MEDIA',
            horaInicio: evento.dataset.horaInicio || '',
            horaFim: evento.dataset.horaFim || '',
            idTurma: evento.dataset.idTurma || '',
            turma: evento.dataset.turma || ''
        };
    });

    const filtroTurma = document.getElementById('filtroTurma');
    const filtroTurmaRotulo = document.getElementById('filtroTurmaRotulo');
    let turmaFiltrada = '';

    function eventoDaTurmaFiltrada(idTurma) {
        return turmaFiltrada === '' || idTurma === turmaFiltrada;
    }

    const modalCronograma = document.getElementById('cronogramaDiaModal');
    const gradeHorarios = document.getElementById('gradeHorarios');
    const dataEventosDia = document.getElementById('dataEventosDia');

    const eventosDaAgenda = eventosDados.map(function (evento) {
        return {
            nome: evento.nome,
            prioridade: evento.prioridade,
            idTurma: evento.idTurma,
            inicio: evento.horaInicio ? new Date(`${evento.dataInicio}T${evento.horaInicio}:00`) : null,
            fim: evento.horaFim ? new Date(`${evento.dataFim}T${evento.horaFim}:00`) : null
        };
    });

    const disponibilidadesDados = Array.from(document.querySelectorAll('.disponibilidade-dado')).map(function (disponibilidade) {
        return {
            data: disponibilidade.dataset.data || '',
            horas: Number(disponibilidade.dataset.horas || 0)
        };
    });

    const planoDados = Array.from(document.querySelectorAll('.plano-dado')).map(function (sessao) {
        return {
            data: sessao.dataset.data || '',
            minutos: Number(sessao.dataset.minutos || 0),
            conteudo: sessao.dataset.conteudo || '',
            evento: sessao.dataset.evento || '',
            dataEvento: sessao.dataset.dataEvento || '',
            idTurma: sessao.dataset.idTurma || '',
            sugestao: sessao.dataset.sugestao === 'true'
        };
    });

    const planoDoDia = document.getElementById('planoDoDia');
    const listaPlanoDoDia = document.getElementById('listaPlanoDoDia');

    const calendario = document.getElementById('calendar');
    const mesExibido = document.getElementById('mesExibido');
    const seletorMes = document.getElementById('seletorMes');
    const mesAnterior = document.getElementById('mesAnterior');
    const mesProximo = document.getElementById('mesProximo');

    const quantidadePrioridadeAlta = document.getElementById('quantidadePrioridadeAlta');
    const quantidadePrioridadeMedia = document.getElementById('quantidadePrioridadeMedia');
    const quantidadePrioridadeBaixa = document.getElementById('quantidadePrioridadeBaixa');
    const quantidadeEventosTotal = document.getElementById('quantidadeEventosTotal');

    const listaEventos = document.getElementById('listaEventos');
    const seletorOrdenacao = document.getElementById('ordenarEventos');
    const botaoAlternarCards = document.getElementById('alternarCards');
    const ultimoEventoDestaque = document.getElementById('ultimoEventoDestaque');
    const ultimoEventoContainer = document.getElementById('ultimoEventoContainer');
    const semEventosNoMes = document.getElementById('semEventosNoMes');

    const hoje = new Date();
    const inicioDeHoje = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());

    let mesAtual = hoje.getMonth();
    let anoAtual = hoje.getFullYear();

    const todosOsCartoesEventos = listaEventos
        ? Array.from(listaEventos.querySelectorAll('.evento-item'))
        : [];

    const ordemOriginalEventos = new Map();

    todosOsCartoesEventos.forEach(function (cartao, indice) {
        ordemOriginalEventos.set(cartao, indice);
    });

    const pesoPrioridade = {
        ALTA: 1,
        MEDIA: 2,
        BAIXA: 3
    };

    function formatarData(data) {
        const ano = data.getFullYear();
        const mes = String(data.getMonth() + 1).padStart(2, '0');
        const dia = String(data.getDate()).padStart(2, '0');

        return `${ano}-${mes}-${dia}`;
    }

    function buscarDisponibilidade(data) {
        return disponibilidadesDados.find(function (disponibilidade) {
            return disponibilidade.data === data;
        });
    }

    function buscarEventosDoDia(data) {
        return eventosDados.filter(function (evento) {
            return evento.dataInicio <= data && data <= evento.dataFim && eventoDaTurmaFiltrada(evento.idTurma);
        });
    }

    function criarMarcadorDisponibilidade(disponibilidade) {
        const marcador = document.createElement('span');

        marcador.classList.add('disponibilidade-marcador');
        marcador.textContent = `Disponibilidade ${disponibilidade.horas}h`;
        marcador.title = `${disponibilidade.horas} horas disponíveis`;

        return marcador;
    }

    function criarMarcadorEvento(eventos, data) {
        if (eventos.length === 0) {
            return null;
        }

        const marcador = document.createElement('button');
        marcador.type = 'button';
        marcador.classList.add('evento-marcador');

        const iniciouHoje = eventos.some(function (evento) {
            return evento.dataInicio === data;
        });

        const terminouHoje = eventos.some(function (evento) {
            return evento.dataFim === data;
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

        marcador.textContent = eventos.length > 1 ? `+${eventos.length} eventos` : eventos[0].nome;

        marcador.title = eventos.map(function (evento) {
            return evento.nome;
        }).join(', ');

        marcador.setAttribute('aria-label', `Ver cronograma do dia ${formatarDataCurta(data)}`);
        marcador.addEventListener('click', function () {
            abrirCronograma(data);
        });

        return marcador;
    }

    function formatarDataCurta(data) {
        const [, mes, dia] = data.split('-');

        return `${dia}/${mes}`;
    }

    function formatarMinutos(minutos) {
        const horas = Math.floor(minutos / 60);
        const resto = minutos % 60;

        if (horas === 0) {
            return `${resto}min`;
        }

        return resto === 0 ? `${horas}h` : `${horas}h${String(resto).padStart(2, '0')}`;
    }

    function buscarPlanoDoDia(data) {
        return planoDados.filter(function (sessao) {
            return sessao.data === data && eventoDaTurmaFiltrada(sessao.idTurma);
        });
    }

    function criarMarcadorEstudo(sessoes, data) {
        const minutosPlanejados = sessoes.reduce(function (total, sessao) {
            return total + (sessao.sugestao ? 0 : sessao.minutos);
        }, 0);
        const minutosSugeridos = sessoes.reduce(function (total, sessao) {
            return total + (sessao.sugestao ? sessao.minutos : 0);
        }, 0);
        const horasSugeridas = Math.ceil(minutosSugeridos / 60) * 60;

        const marcador = document.createElement('button');
        marcador.type = 'button';
        marcador.classList.add('estudo-marcador');
        marcador.textContent = `Estudar ${formatarMinutos(minutosPlanejados + horasSugeridas)}`;
        marcador.title = sessoes.map(function (sessao) {
            return `${sessao.conteudo} · ${formatarMinutos(sessao.minutos)}${sessao.sugestao ? ' (sugestão)' : ''}`;
        }).join('\n');

        if (minutosSugeridos > 0) {
            marcador.classList.add('estudo-sugestao');
            marcador.title += `\n\nInclui ${formatarMinutos(horasSugeridas)} sugeridas: cadastre disponibilidade neste dia.`;
        }
        marcador.setAttribute('aria-label', `Ver plano de estudos do dia ${formatarDataCurta(data)}`);
        marcador.addEventListener('click', function () {
            abrirCronograma(data);
        });

        return marcador;
    }

    function montarPlanoDoDia(data) {
        if (!planoDoDia || !listaPlanoDoDia) {
            return;
        }

        const sessoes = buscarPlanoDoDia(data);

        listaPlanoDoDia.innerHTML = '';
        planoDoDia.hidden = sessoes.length === 0;

        sessoes.forEach(function (sessao) {
            const item = document.createElement('li');
            const descricao = document.createElement('div');
            const conteudo = document.createElement('strong');
            const evento = document.createElement('span');
            const tempo = document.createElement('span');

            conteudo.textContent = sessao.conteudo;
            evento.textContent = `${sessao.evento} · ${formatarDataCurta(sessao.dataEvento)}`;
            tempo.classList.add('plano-dia-tempo');
            tempo.textContent = formatarMinutos(sessao.minutos);

            if (sessao.sugestao) {
                item.classList.add('plano-dia-sugestao');
                tempo.textContent += ' · sugestão';
            }

            descricao.append(conteudo, evento);
            item.append(descricao, tempo);
            listaPlanoDoDia.appendChild(item);
        });
    }

    function abrirCronograma(data) {
        if (!modalCronograma || typeof bootstrap === 'undefined' || typeof montarAgendaDoDia === 'undefined') {
            return;
        }

        const eventosVisiveis = eventosDaAgenda.filter(function (evento) {
            return eventoDaTurmaFiltrada(evento.idTurma);
        });
        montarPlanoDoDia(data);
        const topoPrimeiroEvento = montarAgendaDoDia(gradeHorarios, dataEventosDia, data, eventosVisiveis);
        const corpoModal = modalCronograma.querySelector('.modal-body');

        modalCronograma.addEventListener('shown.bs.modal', function () {
            corpoModal.scrollTop = Math.max(topoPrimeiroEvento - ALTURA_HORA_AGENDA, 0);
        }, { once: true });

        bootstrap.Modal.getOrCreateInstance(modalCronograma).show();
    }

    function eventoComecaNoMesExibido(evento) {
        if (!evento || !evento.dataInicio) {
            return false;
        }

        const mesEsperado = `${anoAtual}-${String(mesAtual + 1).padStart(2, '0')}`;

        return evento.dataInicio.startsWith(mesEsperado);
    }

    function atualizarResumoPrioridades() {
        if (!usuarioEhProfessor) {
            return;
        }

        const eventosDoMes = eventosDados.filter(function (evento) {
            return eventoComecaNoMesExibido(evento) && eventoDaTurmaFiltrada(evento.idTurma);
        });

        let quantidadeAlta = 0;
        let quantidadeMedia = 0;
        let quantidadeBaixa = 0;

        eventosDoMes.forEach(function (evento) {
            const prioridade = (evento.prioridade || 'MEDIA').toUpperCase();

            if (prioridade === 'ALTA') {
                quantidadeAlta++;
            } else if (prioridade === 'BAIXA') {
                quantidadeBaixa++;
            } else {
                quantidadeMedia++;
            }
        });

        if (quantidadePrioridadeAlta) {
            quantidadePrioridadeAlta.textContent = quantidadeAlta;
        }

        if (quantidadePrioridadeMedia) {
            quantidadePrioridadeMedia.textContent = quantidadeMedia;
        }

        if (quantidadePrioridadeBaixa) {
            quantidadePrioridadeBaixa.textContent = quantidadeBaixa;
        }

        if (quantidadeEventosTotal) {
            quantidadeEventosTotal.textContent = eventosDoMes.length;
        }
    }

    function cartaoComecaNoMesExibido(cartao) {
        if (!cartao) {
            return false;
        }

        const inicio = cartao.dataset.inicio || '';
        const mesEsperado = `${anoAtual}-${String(mesAtual + 1).padStart(2, '0')}`;

        return inicio.startsWith(mesEsperado);
    }

    function obterPrioridade(cartao) {
        const prioridade = (cartao.dataset.prioridade || 'MEDIA').toUpperCase();

        return pesoPrioridade[prioridade] || 2;
    }

    function obterDataInicio(cartao) {
        const data = Date.parse(cartao.dataset.inicio || '');

        return Number.isNaN(data) ? Number.MAX_SAFE_INTEGER : data;
    }

    function removerBadgeNovo(cartao) {
        if (!cartao) {
            return;
        }

        cartao.classList.remove('evento-item-fixo');

        const badge = cartao.querySelector('.evento-badge-novo');

        if (badge) {
            badge.remove();
        }
    }

    function adicionarBadgeNovo(cartao) {
        if (!cartao) {
            return;
        }

        cartao.classList.add('evento-item-fixo');

        const identificacao = cartao.querySelector('.evento-identificacao');

        if (identificacao && !identificacao.querySelector('.evento-badge-novo')) {
            const badgeNovo = document.createElement('span');

            badgeNovo.classList.add('evento-badge-novo');
            badgeNovo.textContent = 'Novo';

            identificacao.appendChild(badgeNovo);
        }
    }

    function ordenarCartoes(cartoes) {
        const cartoesOrdenados = cartoes.slice();
        const tipoOrdenacao = seletorOrdenacao ? seletorOrdenacao.value : 'original';

        if (tipoOrdenacao === 'prioridade') {
            cartoesOrdenados.sort(function (cartaoA, cartaoB) {
                const diferenca = obterPrioridade(cartaoA) - obterPrioridade(cartaoB);

                if (diferenca !== 0) {
                    return diferenca;
                }

                return ordemOriginalEventos.get(cartaoA) - ordemOriginalEventos.get(cartaoB);
            });
        }

        if (tipoOrdenacao === 'inicio') {
            cartoesOrdenados.sort(function (cartaoA, cartaoB) {
                const diferenca = obterDataInicio(cartaoA) - obterDataInicio(cartaoB);

                if (diferenca !== 0) {
                    return diferenca;
                }

                return ordemOriginalEventos.get(cartaoA) - ordemOriginalEventos.get(cartaoB);
            });
        }

        if (tipoOrdenacao === 'original') {
            cartoesOrdenados.sort(function (cartaoA, cartaoB) {
                return ordemOriginalEventos.get(cartaoA) - ordemOriginalEventos.get(cartaoB);
            });
        }

        return cartoesOrdenados;
    }

    function atualizarVisaoEventosDoMes() {
        atualizarResumoPrioridades();

        if (!(usuarioEhProfessor || usuarioEhAluno) || !listaEventos) {
            return;
        }

        todosOsCartoesEventos.forEach(function (cartao) {
            removerBadgeNovo(cartao);
            cartao.hidden = true;
            listaEventos.appendChild(cartao);
        });

        const cartoesDoMes = todosOsCartoesEventos.filter(function (cartao) {
            return cartaoComecaNoMesExibido(cartao) && eventoDaTurmaFiltrada(cartao.dataset.idTurma || '');
        });

        if (semEventosNoMes) {
            semEventosNoMes.hidden = cartoesDoMes.length > 0;
        }

        if (cartoesDoMes.length === 0) {
            if (ultimoEventoDestaque) {
                ultimoEventoDestaque.hidden = true;
            }

            listaEventos.hidden = true;
            return;
        }

        const ultimoCartaoDoMes = cartoesDoMes.reduce(function (maisNovo, cartaoAtual) {
            const idMaisNovo = Number(maisNovo.dataset.id || 0);
            const idAtual = Number(cartaoAtual.dataset.id || 0);

            return idAtual > idMaisNovo ? cartaoAtual : maisNovo;
        });

        adicionarBadgeNovo(ultimoCartaoDoMes);
        ultimoCartaoDoMes.hidden = false;

        if (ultimoEventoContainer && ultimoEventoDestaque) {
            ultimoEventoContainer.appendChild(ultimoCartaoDoMes);
            ultimoEventoDestaque.hidden = false;
        }

        const cartoesComunsDoMes = cartoesDoMes.filter(function (cartao) {
            return cartao !== ultimoCartaoDoMes;
        });

        listaEventos.hidden = cartoesComunsDoMes.length === 0;

        const cartoesOrdenados = ordenarCartoes(cartoesComunsDoMes);

        cartoesOrdenados.forEach(function (cartao) {
            cartao.hidden = false;
            listaEventos.appendChild(cartao);
        });
    }

    function atualizarCalendario() {
        if (!calendario || !mesExibido || !seletorMes) {
            return;
        }

        calendario.innerHTML = '';
        mesExibido.textContent = `${meses[mesAtual]} de ${anoAtual}`;
        seletorMes.value = `${anoAtual}-${String(mesAtual + 1).padStart(2, '0')}`;

        // mostra os dias da semana acima das datas
        diasSemana.forEach(function (dia, indice) {
            const cabecalho = document.createElement('div');

            cabecalho.classList.add('calendar-weekday');
            cabecalho.textContent = dia;
            cabecalho.title = nomesDiasSemana[indice];

            calendario.appendChild(cabecalho);
        });

        const primeiroDiaSemana = new Date(anoAtual, mesAtual, 1).getDay();
        const ultimoDiaMes = new Date(anoAtual, mesAtual + 1, 0).getDate();
        const quantidadeCelulas = Math.ceil((primeiroDiaSemana + ultimoDiaMes) / 7) * 7;

        for (let indice = 0; indice < quantidadeCelulas; indice++) {
            const dia = indice - primeiroDiaSemana + 1;
            const celula = document.createElement('div');

            celula.classList.add('day-cell');

            // deixa vazios os lugares que pertencem a outro mes
            if (dia < 1 || dia > ultimoDiaMes) {
                celula.classList.add('adjacent-month', 'calendar-empty');
                celula.setAttribute('aria-hidden', 'true');
                calendario.appendChild(celula);
                continue;
            }

            const data = new Date(anoAtual, mesAtual, dia);
            const dataFormatada = formatarData(data);

            celula.setAttribute('role', 'gridcell');
            celula.setAttribute('aria-label', `${dia} de ${meses[mesAtual]} de ${anoAtual}`);

            const numeroDia = document.createElement('span');

            numeroDia.classList.add('day-number');
            numeroDia.textContent = String(dia).padStart(2, '0');

            const linkCadastro = document.createElement('a');

            linkCadastro.classList.add('day-add');
            linkCadastro.href = `${urlCadastro}?data=${dataFormatada}`;
            linkCadastro.textContent = '+';

            const acao = usuarioEhAluno ? 'Cadastrar disponibilidade' : 'Cadastrar evento';

            linkCadastro.setAttribute('aria-label', `${acao} no dia ${dia} de ${meses[mesAtual]} de ${anoAtual}`);

            const eventosDoDia = buscarEventosDoDia(dataFormatada);

            if (usuarioEhProfessor && eventosDoDia.length > 0) {
                const marcadorEvento = criarMarcadorEvento(eventosDoDia, dataFormatada);

                if (marcadorEvento) {
                    celula.classList.add('tem-evento');
                    celula.appendChild(marcadorEvento);
                }
            }

            const eventosQueComecamNoDia = usuarioEhAluno
                ? eventosDoDia.filter(function (evento) {
                    return evento.dataInicio === dataFormatada;
                })
                : [];

            if (eventosQueComecamNoDia.length > 0) {
                const marcadorEvento = criarMarcadorEvento(eventosQueComecamNoDia, dataFormatada);

                marcadorEvento.classList.add('evento-marcador-aluno');
                celula.appendChild(marcadorEvento);
            }

            if (usuarioEhAluno) {
                const disponibilidadeEncontrada = buscarDisponibilidade(dataFormatada);
                const disponibilidade = disponibilidadeEncontrada || {
                    data: dataFormatada,
                    horas: 0
                };

                const marcadorDisponibilidade = criarMarcadorDisponibilidade(disponibilidade);

                celula.classList.add('tem-disponibilidade');
                celula.appendChild(marcadorDisponibilidade);

                const sessoesDoDia = data < inicioDeHoje ? [] : buscarPlanoDoDia(dataFormatada);

                if (sessoesDoDia.length > 0) {
                    celula.appendChild(criarMarcadorEstudo(sessoesDoDia, dataFormatada));
                }
            }

            celula.appendChild(numeroDia);

            if (data < inicioDeHoje) {
                celula.classList.add('dia-passado');
            } else if (eventosQueComecamNoDia.length === 0) {
                celula.appendChild(linkCadastro);
            }

            calendario.appendChild(celula);
        }

        atualizarVisaoEventosDoMes();
    }

    if (mesAnterior) {
        mesAnterior.addEventListener('click', function () {
            mesAtual--;

            if (mesAtual < 0) {
                mesAtual = 11;
                anoAtual--;
            }

            atualizarCalendario();
        });
    }

    if (mesProximo) {
        mesProximo.addEventListener('click', function () {
            mesAtual++;

            if (mesAtual > 11) {
                mesAtual = 0;
                anoAtual++;
            }

            atualizarCalendario();
        });
    }

    if (seletorMes) {
        seletorMes.addEventListener('change', function () {
            if (!this.value) {
                return;
            }

            const partes = this.value.split('-');
            const anoSelecionado = Number(partes[0]);
            const mesSelecionado = Number(partes[1]) - 1;

            if (!Number.isInteger(anoSelecionado) || !Number.isInteger(mesSelecionado)
                || anoSelecionado < 1 || mesSelecionado < 0 || mesSelecionado > 11) {
                return;
            }

            anoAtual = anoSelecionado;
            mesAtual = mesSelecionado;

            atualizarCalendario();
        });
    }

    const popup = document.querySelector('.popup-sucesso');

    if (popup) {
        setTimeout(function () {
            popup.classList.add('ocultar');

            setTimeout(function () {
                popup.remove();
            }, 400);
        }, 3500);
    }

    if (listaEventos && botaoAlternarCards) {
        botaoAlternarCards.addEventListener('click', function () {
            const estaOculto = listaEventos.classList.toggle('cards-ocultos');

            this.textContent = estaOculto ? 'Mostrar cards' : 'Ocultar cards';
            this.setAttribute('aria-expanded', String(!estaOculto));
        });
    }

    document.addEventListener('click', function (event) {
        const botao = event.target.closest('.botao-alternar-conteudos');

        if (!botao) {
            return;
        }

        const lista = document.getElementById(botao.getAttribute('aria-controls'));

        if (!lista) {
            return;
        }

        const expandido = botao.getAttribute('aria-expanded') === 'true';

        lista.hidden = expandido;
        botao.setAttribute('aria-expanded', String(!expandido));
        botao.textContent = expandido ? 'Mostrar conteúdos' : 'Ocultar conteúdos';
    });

    if (filtroTurma && filtroTurmaRotulo) {
        const turmas = new Map();

        eventosDados.forEach(function (evento) {
            if (evento.idTurma && !turmas.has(evento.idTurma)) {
                turmas.set(evento.idTurma, evento.turma || 'Turma não identificada');
            }
        });

        Array.from(turmas.entries())
            .sort(function (turmaA, turmaB) {
                return turmaA[1].localeCompare(turmaB[1], 'pt-BR');
            })
            .forEach(function ([idTurma, nomeTurma]) {
                const opcao = document.createElement('option');

                opcao.value = idTurma;
                opcao.textContent = nomeTurma;
                filtroTurma.appendChild(opcao);
            });

        filtroTurmaRotulo.hidden = turmas.size < 2;

        filtroTurma.addEventListener('change', function () {
            turmaFiltrada = filtroTurma.value;
            atualizarCalendario();
        });
    }

    if (seletorOrdenacao) {
        seletorOrdenacao.addEventListener('change', function () {
            atualizarVisaoEventosDoMes();
        });
    }

    document.querySelectorAll('.form-excluir-evento').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const confirmar = confirm('Deseja realmente excluir este evento?');

            if (!confirmar) {
                event.preventDefault();
            }
        });
    });

    const confirmarRefazerModal = document.getElementById('confirmarRefazerModal');
    const formRefazerConteudo = document.getElementById('formRefazerConteudo');
    const tituloConteudoRefazer = document.getElementById('tituloConteudoRefazer');

    document.addEventListener('click', function (event) {
        const botao = event.target.closest('.botao-refazer');

        if (!botao || !confirmarRefazerModal || !formRefazerConteudo || typeof bootstrap === 'undefined') {
            return;
        }

        formRefazerConteudo.action = botao.dataset.acao;
        tituloConteudoRefazer.textContent = botao.dataset.titulo || 'este conteúdo';
        bootstrap.Modal.getOrCreateInstance(confirmarRefazerModal).show();
    });

    atualizarCalendario();
});