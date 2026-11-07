// calendario organiza eventos disponibilidade e planos de estudo com filtros por turma
// preparar calendario configura filtros navegacao agendas planos de estudo e acoes dos eventos
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

    // ler dados do calendario extrai identificacao datas horarios prioridade e turma de cada evento
    const eventosDados = Array.from(document.querySelectorAll('.evento-dado')).map(function (evento) {
        return {
            id: evento.dataset.id || '',
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

    // conferir filtro de turma aceita todas as turmas ou apenas a turma selecionada
    function eventoDaTurmaFiltrada(idTurma) {
        return turmaFiltrada === '' || idTurma === turmaFiltrada;
    }

    const modalCronograma = document.getElementById('cronogramaDiaModal');
    const gradeHorarios = document.getElementById('gradeHorarios');
    const dataEventosDia = document.getElementById('dataEventosDia');

    // preparar evento da agenda transforma as datas e horarios em valores usados na grade
    const eventosDaAgenda = eventosDados.map(function (evento) {
        return {
            id: evento.id,
            nome: evento.nome,
            prioridade: evento.prioridade,
            idTurma: evento.idTurma,
            inicio: evento.horaInicio ? new Date(`${evento.dataInicio}T${evento.horaInicio}:00`) : null,
            fim: evento.horaFim ? new Date(`${evento.dataFim}T${evento.horaFim}:00`) : null
        };
    });

    // ler horas disponiveis extrai a data e a quantidade de horas para o calendario
    const disponibilidadesDados = Array.from(document.querySelectorAll('.disponibilidade-dado')).map(function (disponibilidade) {
        return {
            data: disponibilidade.dataset.data || '',
            horas: Number(disponibilidade.dataset.horas || 0)
        };
    });

    // ler sessao de estudo extrai data duracao conteudo evento turma e indicacao de sugestao
    const planoDados = Array.from(document.querySelectorAll('.plano-dado')).map(function (sessao) {
        return {
            data: sessao.dataset.data || '',
            minutos: Number(sessao.dataset.minutos || 0),
            conteudo: sessao.dataset.conteudo || '',
            evento: sessao.dataset.evento || '',
            dataEvento: sessao.dataset.dataEvento || '',
            idTurma: sessao.dataset.idTurma || '',
            idConteudo: sessao.dataset.idConteudo || '',
            sugestao: sessao.dataset.sugestao === 'true'
        };
    });

    const planoEstudoModal = document.getElementById('planoEstudoModal');
    const dataPlanoEstudo = document.getElementById('dataPlanoEstudo');
    const turmasPlanoDoDia = document.getElementById('turmasPlanoDoDia');
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

    const cartoesEventos = document.getElementById('cartoesEventos');
    const detalheEventoModal = document.getElementById('detalheEventoModal');
    const detalheEventoCorpo = document.getElementById('detalheEventoCorpo');
    const voltarAgendaDia = document.getElementById('voltarAgendaDia');
    let dataAgendaAberta = null;

    const hoje = new Date();
    const inicioDeHoje = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());

    let mesAtual = hoje.getMonth();
    let anoAtual = hoje.getFullYear();


    // formatar data da navegacao monta ano mes e dia usando a data local
    function formatarData(data) {
        const ano = data.getFullYear();
        const mes = String(data.getMonth() + 1).padStart(2, '0');
        const dia = String(data.getDate()).padStart(2, '0');

        return `${ano}-${mes}-${dia}`;
    }

    // buscar disponibilidade do dia procura as horas cadastradas para uma data
    function buscarDisponibilidade(data) {
        // comparar data disponivel identifica o registro da data procurada
        return disponibilidadesDados.find(function (disponibilidade) {
            return disponibilidade.data === data;
        });
    }

    // buscar eventos do dia seleciona os eventos da data e da turma filtrada
    function buscarEventosDoDia(data) {
        // conferir evento do dia verifica o intervalo de datas e o filtro de turma
        return eventosDados.filter(function (evento) {
            return evento.dataInicio <= data && data <= evento.dataFim && eventoDaTurmaFiltrada(evento.idTurma);
        });
    }

    // montar marcador de horas mostra a disponibilidade em horas no calendario
    function criarMarcadorDisponibilidade(disponibilidade) {
        const marcador = document.createElement('span');

        marcador.classList.add('disponibilidade-marcador');
        marcador.textContent = `Disponibilidade ${disponibilidade.horas}h`;
        marcador.title = `${disponibilidade.horas} horas disponíveis`;

        return marcador;
    }

    // montar marcador de eventos mostra quantidade ou nome e abre o cronograma do dia
    function criarMarcadorEvento(eventos, data) {
        // criar marcador de evento ignora uma lista de eventos vazia
        if (eventos.length === 0) {
            return null;
        }

        const marcador = document.createElement('button');
        marcador.type = 'button';
        marcador.classList.add('evento-marcador');

        // conferir inicio do marcador identifica eventos que comecam na data exibida
        const iniciouHoje = eventos.some(function (evento) {
            return evento.dataInicio === data;
        });

        // conferir fim do marcador identifica eventos que terminam na data exibida
        const terminouHoje = eventos.some(function (evento) {
            return evento.dataFim === data;
        });

        // marcar evento diario aplica o estilo de inicio e fim no mesmo dia
        if (iniciouHoje && terminouHoje) {
            marcador.classList.add('evento-unico');
        }
        // marcar primeiro dia do evento aplica o estilo de inicio
        else if (iniciouHoje) {
            marcador.classList.add('evento-inicio');
        }
        // marcar ultimo dia do evento distingue o fim dos dias intermediarios
        else if (terminouHoje) {
            marcador.classList.add('evento-fim');
        } else {
            marcador.classList.add('evento-meio');
        }

        // buscar prioridade do dia procura o primeiro nivel presente entre os eventos
        const prioridade = ['ALTA', 'MEDIA', 'BAIXA'].find(function (nivel) {
            // comparar prioridade verifica se o evento pertence ao nivel procurado
            return eventos.some(function (evento) {
                return (evento.prioridade || 'MEDIA').toUpperCase() === nivel;
            });
        }) || 'MEDIA';
        marcador.classList.add(`evento-prioridade-${prioridade.toLowerCase()}`);

        marcador.textContent = eventos.length > 1 ? `+${eventos.length} eventos` : eventos[0].nome;

        // descrever eventos do dia retorna os nomes usados no marcador
        marcador.title = eventos.map(function (evento) {
            return evento.nome;
        }).join(', ');

        marcador.setAttribute('aria-label', `Ver cronograma do dia ${formatarDataCurta(data)}`);
        // abrir cronograma pelo marcador mostra a agenda da data clicada
        marcador.addEventListener('click', function () {
            abrirCronograma(data);
        });

        return marcador;
    }

    // formatar data curta transforma ano mes e dia em dia e mes
    function formatarDataCurta(data) {
        const [, mes, dia] = data.split('-');

        return `${dia}/${mes}`;
    }

    // formatar tempo de estudo transforma minutos em uma duracao curta
    function formatarMinutos(minutos) {
        const horas = Math.floor(minutos / 60);
        const resto = minutos % 60;

        // formatar minutos de estudo mostra apenas minutos quando nao completa uma hora
        if (horas === 0) {
            return `${resto}min`;
        }

        return resto === 0 ? `${horas}h` : `${horas}h${String(resto).padStart(2, '0')}`;
    }

    // buscar plano do dia filtra as sessoes pela data e pela turma escolhida
    function buscarPlanoDoDia(data) {
        // conferir sessao do plano compara a data e aplica o filtro de turma
        return planoDados.filter(function (sessao) {
            return sessao.data === data && eventoDaTurmaFiltrada(sessao.idTurma);
        });
    }

    // montar marcador de estudo soma tempos planejados e sugeridos e abre o plano do dia
    function criarMarcadorEstudo(sessoes, data) {
        // somar minutos planejados acumula apenas sessoes que nao sao sugestoes
        const minutosPlanejados = sessoes.reduce(function (total, sessao) {
            return total + (sessao.sugestao ? 0 : sessao.minutos);
        }, 0);
        // somar minutos sugeridos acumula apenas as sessoes sugeridas
        const minutosSugeridos = sessoes.reduce(function (total, sessao) {
            return total + (sessao.sugestao ? sessao.minutos : 0);
        }, 0);
        const horasSugeridas = Math.ceil(minutosSugeridos / 15) * 15;

        const marcador = document.createElement('button');
        marcador.type = 'button';
        marcador.classList.add('estudo-marcador');
        marcador.textContent = `Estudar ${formatarMinutos(minutosPlanejados + horasSugeridas)}`;
        // descrever sessao no marcador mostra conteudo duracao e indicacao de sugestao
        marcador.title = sessoes.map(function (sessao) {
            return `${sessao.conteudo} · ${formatarMinutos(sessao.minutos)}${sessao.sugestao ? ' (sugestão)' : ''}`;
        }).join('\n');

        // destacar estudo sugerido informa as horas sugeridas que precisam de disponibilidade
        if (minutosSugeridos > 0) {
            marcador.classList.add('estudo-sugestao');
            marcador.title += `\n\nInclui ${formatarMinutos(horasSugeridas)} sugeridas: cadastre disponibilidade neste dia.`;
        }
        marcador.setAttribute('aria-label', `Ver plano de estudos do dia ${formatarDataCurta(data)}`);
        // abrir plano pelo marcador mostra as sessoes da data clicada
        marcador.addEventListener('click', function () {
            abrirPlanoDoDia(data);
        });

        return marcador;
    }

    // agrupar plano por conteudo reune sessoes do mesmo conteudo evento e data de evento
    function agruparPlanoPorConteudo(sessoes) {
        const grupos = new Map();

        // acumular sessao de estudo soma minutos e preserva a indicacao de sugestao no grupo
        sessoes.forEach(function (sessao) {
            const chave = `${sessao.idConteudo}|${sessao.evento}|${sessao.dataEvento}`;
            const grupo = grupos.get(chave) || Object.assign({}, sessao, { minutos: 0, sugestao: false });

            grupo.minutos += sessao.minutos;
            grupo.sugestao = grupo.sugestao || sessao.sugestao;
            grupos.set(chave, grupo);
        });

        return Array.from(grupos.values());
    }

    // abrir plano do dia monta as sessoes agrupadas e os links das turmas
    function abrirPlanoDoDia(data) {
        // abrir plano diario exige a janela a lista e os recursos para exibir o plano
        if (!planoEstudoModal || !listaPlanoDoDia || typeof bootstrap === 'undefined') {
            return;
        }

        const sessoes = agruparPlanoPorConteudo(buscarPlanoDoDia(data));
        const nomesTurmas = new Map();

        // relacionar nome de turma guarda o nome associado ao codigo presente no evento
        eventosDados.forEach(function (evento) {
            nomesTurmas.set(evento.idTurma, evento.turma || 'Turma');
        });

        dataPlanoEstudo.textContent = new Date(`${data}T12:00:00`).toLocaleDateString('pt-BR', {
            weekday: 'long', day: '2-digit', month: 'long'
        });

        listaPlanoDoDia.innerHTML = '';
        // montar sessao do plano mostra conteudo evento data duracao e sugestao
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

            // identificar sessao sugerida destaca sessoes de estudo sugeridas no plano do dia
            if (sessao.sugestao) {
                item.classList.add('plano-dia-sugestao');
                tempo.textContent += ' · sugestão';
            }

            descricao.append(conteudo, evento);
            item.append(descricao, tempo);
            listaPlanoDoDia.appendChild(item);
        });

        turmasPlanoDoDia.innerHTML = '';
        // listar turmas do plano extrai o codigo da turma de cada sessao
        Array.from(new Set(sessoes.map(function (sessao) {
            return sessao.idTurma;
        // montar link da turma cria o acesso para a turma presente no plano
        }))).forEach(function (idTurma) {
            const link = document.createElement('a');

            link.href = `/turmas/${encodeURIComponent(idTurma)}`;
            link.classList.add('botao-ir-turma');
            link.textContent = `Ir para ${nomesTurmas.get(idTurma) || 'a turma'}`;
            turmasPlanoDoDia.appendChild(link);
        });

        bootstrap.Modal.getOrCreateInstance(planoEstudoModal).show();
    }

    // abrir cronograma do dia monta a grade da turma filtrada e prepara a rolagem
    function abrirCronograma(data) {
        // abrir cronograma exige a janela e os recursos para montar a agenda
        if (!modalCronograma || typeof bootstrap === 'undefined' || typeof montarAgendaDoDia === 'undefined') {
            return;
        }

        // filtrar eventos da agenda aplica a turma escolhida aos eventos da grade
        const eventosVisiveis = eventosDaAgenda.filter(function (evento) {
            return eventoDaTurmaFiltrada(evento.idTurma);
        });
        const topoPrimeiroEvento = montarAgendaDoDia(gradeHorarios, dataEventosDia, data, eventosVisiveis);
        const corpoModal = modalCronograma.querySelector('.modal-body');
        dataAgendaAberta = data;

        // rolar cronograma aberto posiciona a janela perto do primeiro evento do dia
        modalCronograma.addEventListener('shown.bs.modal', function () {
            corpoModal.scrollTop = Math.max(topoPrimeiroEvento - ALTURA_HORA_AGENDA, 0);
        }, { once: true });

        bootstrap.Modal.getOrCreateInstance(modalCronograma).show();
    }

    // conferir mes do evento verifica se o inicio pertence ao mes exibido
    function eventoComecaNoMesExibido(evento) {
        // filtrar eventos do mes ignora eventos sem data de inicio
        if (!evento || !evento.dataInicio) {
            return false;
        }

        const mesEsperado = `${anoAtual}-${String(mesAtual + 1).padStart(2, '0')}`;

        return evento.dataInicio.startsWith(mesEsperado);
    }

    // atualizar resumo de prioridades conta os eventos do mes por nivel de prioridade
    function atualizarResumoPrioridades() {
        // resumir prioridades limita o resumo aos perfis de aluno e professor
        if (!usuarioEhProfessor && !usuarioEhAluno) {
            return;
        }

        // filtrar eventos do resumo limita os eventos ao mes exibido e a turma escolhida
        const eventosDoMes = eventosDados.filter(function (evento) {
            return eventoComecaNoMesExibido(evento) && eventoDaTurmaFiltrada(evento.idTurma);
        });

        let quantidadeAlta = 0;
        let quantidadeMedia = 0;
        let quantidadeBaixa = 0;

        // contar prioridade do evento soma os niveis alto medio e baixo separadamente
        eventosDoMes.forEach(function (evento) {
            const prioridade = (evento.prioridade || 'MEDIA').toUpperCase();

            // contar prioridade alta incrementa o total de eventos com prioridade alta
            if (prioridade === 'ALTA') {
                quantidadeAlta++;
            }
            // contar prioridade baixa distingue eventos de prioridade baixa dos de prioridade media
            else if (prioridade === 'BAIXA') {
                quantidadeBaixa++;
            } else {
                quantidadeMedia++;
            }
        });

        // mostrar total de prioridade alta atualiza o contador disponivel na pagina
        if (quantidadePrioridadeAlta) {
            quantidadePrioridadeAlta.textContent = quantidadeAlta;
        }

        // mostrar total de prioridade media atualiza o contador disponivel na pagina
        if (quantidadePrioridadeMedia) {
            quantidadePrioridadeMedia.textContent = quantidadeMedia;
        }

        // mostrar total de prioridade baixa atualiza o contador disponivel na pagina
        if (quantidadePrioridadeBaixa) {
            quantidadePrioridadeBaixa.textContent = quantidadeBaixa;
        }

        // mostrar total de eventos atualiza a quantidade de eventos do mes
        if (quantidadeEventosTotal) {
            quantidadeEventosTotal.textContent = eventosDoMes.length;
        }
    }

    // devolver cartoes de detalhes retorna os eventos abertos para a lista original
    function devolverCartaoAberto() {
        // devolver cartao do evento exige a area de detalhes e a lista original dos cartoes
        if (!detalheEventoCorpo || !cartoesEventos) {
            return;
        }

        // retornar cartao de evento recoloca o item na area de cartoes
        Array.from(detalheEventoCorpo.querySelectorAll('.evento-item')).forEach(function (cartao) {
            cartoesEventos.appendChild(cartao);
        });
    }

    // recolher conteudos do evento fecha a lista e restaura o texto do botao
    function recolherConteudos(cartao) {
        const botao = cartao.querySelector('.botao-alternar-conteudos');
        const lista = botao ? document.getElementById(botao.getAttribute('aria-controls')) : null;

        // recolher conteudos do evento oculta a lista e atualiza o botao de exibicao
        if (botao && lista) {
            lista.hidden = true;
            botao.setAttribute('aria-expanded', 'false');
            botao.textContent = 'Mostrar conteúdos';
        }
    }

    // abrir detalhe do evento move o cartao escolhido para a janela e fecha a agenda quando preciso
    function abrirDetalheEvento(idEvento) {
        // abrir detalhes do evento exige as areas da pagina e o recurso de janelas
        if (!detalheEventoModal || !detalheEventoCorpo || !cartoesEventos || typeof bootstrap === 'undefined') {
            return;
        }

        const cartao = cartoesEventos.querySelector(`.evento-item[data-id="${CSS.escape(idEvento)}"]`);

        // localizar cartao do evento encerra quando o cartao solicitado nao existe
        if (!cartao) {
            return;
        }

        devolverCartaoAberto();
        recolherConteudos(cartao);
        detalheEventoCorpo.appendChild(cartao);

        // mostrar retorno para agenda exibe o botao apenas quando ha um dia de origem
        if (voltarAgendaDia) {
            voltarAgendaDia.hidden = !dataAgendaAberta;
        }

        // mostrar janela de detalhe exibe os dados do evento selecionado
        const mostrarDetalhe = function () {
            bootstrap.Modal.getOrCreateInstance(detalheEventoModal).show();
        };

        // trocar agenda por detalhes fecha a agenda aberta antes de mostrar o evento
        if (modalCronograma && modalCronograma.classList.contains('show')) {
            modalCronograma.addEventListener('hidden.bs.modal', mostrarDetalhe, { once: true });
            bootstrap.Modal.getInstance(modalCronograma).hide();
        } else {
            mostrarDetalhe();
        }
    }

    // atualizar calendario desenha dias eventos disponibilidade e estudos e atualiza os totais
    function atualizarCalendario() {
        // atualizar calendario exige a grade o titulo do mes e o seletor
        if (!calendario || !mesExibido || !seletorMes) {
            return;
        }

        calendario.innerHTML = '';
        mesExibido.textContent = `${meses[mesAtual]} de ${anoAtual}`;
        seletorMes.value = `${anoAtual}-${String(mesAtual + 1).padStart(2, '0')}`;


        // montar cabecalho da semana exibe o nome curto do dia e sua descricao completa
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


            // preencher dias fora do mes cria celulas vazias para completar a grade
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

            // mostrar eventos do professor cria o marcador quando o dia possui eventos
            if (usuarioEhProfessor && eventosDoDia.length > 0) {
                const marcadorEvento = criarMarcadorEvento(eventosDoDia, dataFormatada);

                // inserir marcador do professor acrescenta o marcador encontrado na celula do dia
                if (marcadorEvento) {
                    celula.classList.add('tem-evento');
                    celula.appendChild(marcadorEvento);
                }
            }

            const eventosQueComecamNoDia = usuarioEhAluno
                // filtrar inicios para aluno mostra os eventos que comecam na data da celula
                ? eventosDoDia.filter(function (evento) {
                    return evento.dataInicio === dataFormatada;
                })
                : [];

            // mostrar eventos que iniciam no dia acrescenta o marcador com os eventos dessa data
            if (eventosQueComecamNoDia.length > 0) {
                const marcadorEvento = criarMarcadorEvento(eventosQueComecamNoDia, dataFormatada);

                marcadorEvento.classList.add('evento-marcador-aluno');
                celula.appendChild(marcadorEvento);
            }

            // mostrar planejamento do aluno acrescenta disponibilidade e sessoes de estudo no calendario
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

                // mostrar sessoes do dia inclui o marcador de estudo quando existem sessoes
                if (sessoesDoDia.length > 0) {
                    celula.appendChild(criarMarcadorEstudo(sessoesDoDia, dataFormatada));
                }
            }

            celula.appendChild(numeroDia);

            // identificar dia passado aplica o estilo das datas anteriores a hoje
            if (data < inicioDeHoje) {
                celula.classList.add('dia-passado');
            }
            // oferecer cadastro no dia inclui o link quando nenhum evento comeca nessa data
            else if (eventosQueComecamNoDia.length === 0) {
                celula.appendChild(linkCadastro);
            }

            calendario.appendChild(celula);
        }

        atualizarResumoPrioridades();
    }

    // navegar ao mes anterior configura o botao para recuar o calendario
    if (mesAnterior) {
        // voltar mes do calendario ajusta o ano quando necessario e redesenha o mes
        mesAnterior.addEventListener('click', function () {
            mesAtual--;

            // recuar ano do calendario passa para dezembro do ano anterior ao sair de janeiro
            if (mesAtual < 0) {
                mesAtual = 11;
                anoAtual--;
            }

            atualizarCalendario();
        });
    }

    // navegar ao mes seguinte configura o botao para avancar o calendario
    if (mesProximo) {
        // avancar mes do calendario ajusta o ano quando necessario e redesenha o mes
        mesProximo.addEventListener('click', function () {
            mesAtual++;

            // avancar ano do calendario passa para janeiro do proximo ano ao sair de dezembro
            if (mesAtual > 11) {
                mesAtual = 0;
                anoAtual++;
            }

            atualizarCalendario();
        });
    }

    // selecionar mes do calendario configura a troca de mes pelo campo de selecao
    if (seletorMes) {
        // escolher mes do calendario valida ano e mes antes de atualizar as datas
        seletorMes.addEventListener('change', function () {
            // ignorar mes vazio encerra a alteracao quando nenhum mes foi informado
            if (!this.value) {
                return;
            }

            const partes = this.value.split('-');
            const anoSelecionado = Number(partes[0]);
            const mesSelecionado = Number(partes[1]) - 1;

            // validar mes escolhido rejeita ano ou mes fora dos valores permitidos
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

    // ocultar aviso do calendario programa o desaparecimento do aviso temporario
    if (popup) {
        // ocultar aviso de sucesso inicia a saida visual e agenda a remocao da mensagem
        setTimeout(function () {
            popup.classList.add('ocultar');

            // remover aviso de sucesso retira a mensagem depois da animacao
            setTimeout(function () {
                popup.remove();
            }, 400);
        }, 3500);
    }

    // alternar conteudos do evento mostra ou oculta a lista e atualiza o botao
    document.addEventListener('click', function (event) {
        const botao = event.target.closest('.botao-alternar-conteudos');

        // alternar conteudos do evento ignora cliques que nao correspondem ao botao
        if (!botao) {
            return;
        }

        const lista = document.getElementById(botao.getAttribute('aria-controls'));

        // localizar lista de conteudos encerra quando a lista do evento nao existe
        if (!lista) {
            return;
        }

        const expandido = botao.getAttribute('aria-expanded') === 'true';

        lista.hidden = expandido;
        botao.setAttribute('aria-expanded', String(!expandido));
        botao.textContent = expandido ? 'Mostrar conteúdos' : 'Ocultar conteúdos';
    });

    // filtrar calendario por turma configura as opcoes e a atualizacao do filtro
    if (filtroTurma && filtroTurmaRotulo) {
        const turmas = new Map();

        // reunir turmas dos eventos guarda cada turma uma vez para montar o filtro
        eventosDados.forEach(function (evento) {
            // reunir turmas do calendario inclui cada turma identificada apenas uma vez
            if (evento.idTurma && !turmas.has(evento.idTurma)) {
                turmas.set(evento.idTurma, evento.turma || 'Turma não identificada');
            }
        });

        Array.from(turmas.entries())
            // ordenar filtro de turmas compara os nomes em ordem alfabetica brasileira
            .sort(function (turmaA, turmaB) {
                return turmaA[1].localeCompare(turmaB[1], 'pt-BR');
            })
            // montar opcao do filtro adiciona o nome e o codigo da turma ao seletor
            .forEach(function ([idTurma, nomeTurma]) {
                const opcao = document.createElement('option');

                opcao.value = idTurma;
                opcao.textContent = nomeTurma;
                filtroTurma.appendChild(opcao);
            });

        filtroTurmaRotulo.hidden = turmas.size < 2;

        // aplicar filtro de turma guarda a escolha e redesenha o calendario
        filtroTurma.addEventListener('change', function () {
            turmaFiltrada = filtroTurma.value;
            atualizarCalendario();
        });
    }

    // abrir eventos pela agenda configura cliques e atalhos de teclado nos cartoes
    if (gradeHorarios) {
        // abrir evento por clique mostra os detalhes do cartao selecionado na agenda
        gradeHorarios.addEventListener('click', function (event) {
            const card = event.target.closest('.agenda-evento-clicavel');

            // abrir cartao clicado mostra os detalhes do evento encontrado no clique
            if (card) {
                abrirDetalheEvento(card.dataset.idEvento);
            }
        });

        // abrir evento pelo teclado aceita enter ou espaco no cartao da agenda
        gradeHorarios.addEventListener('keydown', function (event) {
            const card = event.target.closest('.agenda-evento-clicavel');

            // abrir cartao pelo teclado usa enter ou espaco para mostrar os detalhes do evento
            if (card && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault();
                abrirDetalheEvento(card.dataset.idEvento);
            }
        });
    }

    // restaurar cartao ao fechar configura o retorno do cartao para sua lista original
    if (detalheEventoModal) {
        detalheEventoModal.addEventListener('hidden.bs.modal', devolverCartaoAberto);
    }

    // voltar ao dia da agenda configura o botao de retorno a partir dos detalhes
    if (voltarAgendaDia) {
        // voltar para agenda fecha os detalhes e prepara o retorno a data aberta
        voltarAgendaDia.addEventListener('click', function () {
            // validar retorno a agenda exige uma data de origem e a janela de detalhes
            if (!dataAgendaAberta || !detalheEventoModal) {
                return;
            }

            const data = dataAgendaAberta;
            // reabrir cronograma retorna a agenda depois do fechamento dos detalhes
            detalheEventoModal.addEventListener('hidden.bs.modal', function () {
                abrirCronograma(data);
            }, { once: true });
            bootstrap.Modal.getInstance(detalheEventoModal).hide();
        });
    }

    // configurar delete evento associa a confirmacao aos formularios de exclusao
    document.querySelectorAll('.form-excluir-evento').forEach(function (form) {
        // confirmar delete evento impede a exclusao quando a pessoa cancela
        form.addEventListener('submit', function (event) {
            const confirmar = confirm('Deseja realmente excluir este evento?');

            // cancelar acao nao confirmada impede o envio quando a pessoa recusa a confirmacao
            if (!confirmar) {
                event.preventDefault();
            }
        });
    });

    const confirmarRefazerModal = document.getElementById('confirmarRefazerModal');
    const formRefazerConteudo = document.getElementById('formRefazerConteudo');
    const tituloConteudoRefazer = document.getElementById('tituloConteudoRefazer');

    // preparar refazer conteudo define a acao e o titulo antes de abrir a confirmacao
    document.addEventListener('click', function (event) {
        const botao = event.target.closest('.botao-refazer');

        // confirmar refazer conteudo exige o botao o formulario e os recursos da janela
        if (!botao || !confirmarRefazerModal || !formRefazerConteudo || typeof bootstrap === 'undefined') {
            return;
        }

        formRefazerConteudo.action = botao.dataset.acao;
        tituloConteudoRefazer.textContent = botao.dataset.titulo || 'este conteúdo';

        // mostrar confirmacao de refazer exibe a janela para reiniciar o conteudo
        const mostrarConfirmacao = function () {
            bootstrap.Modal.getOrCreateInstance(confirmarRefazerModal).show();
        };

        // trocar detalhes por confirmacao fecha os detalhes abertos antes de confirmar refazer o conteudo
        if (detalheEventoModal && detalheEventoModal.classList.contains('show')) {
            detalheEventoModal.addEventListener('hidden.bs.modal', mostrarConfirmacao, { once: true });
            bootstrap.Modal.getInstance(detalheEventoModal).hide();
        } else {
            mostrarConfirmacao();
        }
    });

    atualizarCalendario();
});