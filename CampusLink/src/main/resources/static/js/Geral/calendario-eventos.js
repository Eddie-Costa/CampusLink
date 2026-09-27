document.addEventListener('DOMContentLoaded', function () {
    const meses = [
        'Janeiro', 'Fevereiro', 'Março', 'Abril',
        'Maio', 'Junho', 'Julho', 'Agosto',
        'Setembro', 'Outubro', 'Novembro', 'Dezembro'
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
            prioridade: evento.dataset.prioridade || 'MEDIA'
        };
    });

    const disponibilidadesDados = Array.from(document.querySelectorAll('.disponibilidade-dado')).map(function (disponibilidade) {
        return {
            data: disponibilidade.dataset.data || '',
            horas: Number(disponibilidade.dataset.horas || 0)
        };
    });

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
            return evento.dataInicio <= data && data <= evento.dataFim;
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
        if (!usuarioEhProfessor || eventos.length === 0) {
            return null;
        }

        const marcador = document.createElement('span');

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

        if (eventos.length > 1) {
            marcador.textContent = `${eventos.length} eventos`;
        } else if (iniciouHoje) {
            marcador.textContent = eventos[0].nome;
        } else {
            marcador.textContent = '';
        }

        marcador.title = eventos.map(function (evento) {
            return evento.nome;
        }).join(', ');

        return marcador;
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

        const eventosDoMes = eventosDados.filter(eventoComecaNoMesExibido);

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

        if (!usuarioEhProfessor || !listaEventos) {
            return;
        }

        todosOsCartoesEventos.forEach(function (cartao) {
            removerBadgeNovo(cartao);

            cartao.hidden = true;

            listaEventos.appendChild(cartao);
        });

        const cartoesDoMes = todosOsCartoesEventos.filter(cartaoComecaNoMesExibido);

        if (semEventosNoMes) {
            semEventosNoMes.hidden = cartoesDoMes.length > 0;
        }

        if (cartoesDoMes.length === 0) {
            if (ultimoEventoDestaque) {
                ultimoEventoDestaque.hidden = true;
            }

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

        seletorMes.value =
            `${anoAtual}-${String(mesAtual + 1).padStart(2, '0')}`;

        const primeiroDiaSemana =
            new Date(anoAtual, mesAtual, 1).getDay();

        const ultimoDiaMes =
            new Date(anoAtual, mesAtual + 1, 0).getDate();

        const quantidadeCelulas =
            Math.ceil((primeiroDiaSemana + ultimoDiaMes) / 7) * 7;

        for (let indice = 0; indice < quantidadeCelulas; indice++) {
            const data =
                new Date(
                    anoAtual,
                    mesAtual,
                    indice - primeiroDiaSemana + 1
                );

            const dataFormatada = formatarData(data);

            const pertenceAoMesAtual =
                data.getMonth() === mesAtual &&
                data.getFullYear() === anoAtual;

            const celula =
                document.createElement('div');

            celula.classList.add('day-cell');
            celula.setAttribute('role', 'gridcell');

            if (!pertenceAoMesAtual) {
                celula.classList.add('adjacent-month');
            }

            const numeroDia =
                document.createElement('span');

            numeroDia.classList.add('day-number');
            numeroDia.textContent =
                String(data.getDate()).padStart(2, '0');

            const linkCadastro =
                document.createElement('a');

            linkCadastro.classList.add('day-add');
            linkCadastro.href =
                `${urlCadastro}?data=${dataFormatada}`;

            linkCadastro.textContent = '+';

            const acao =
                usuarioEhAluno
                    ? 'Cadastrar disponibilidade'
                    : 'Cadastrar evento';

            linkCadastro.setAttribute(
                'aria-label',
                `${acao} no dia ${data.getDate()}`
            );

            const eventosDoDia =
                buscarEventosDoDia(dataFormatada);

            if (usuarioEhProfessor && eventosDoDia.length > 0) {
                const marcadorEvento =
                    criarMarcadorEvento(
                        eventosDoDia,
                        dataFormatada
                    );

                if (marcadorEvento) {
                    celula.classList.add('tem-evento');
                    celula.appendChild(marcadorEvento);
                }
            }

            if (usuarioEhAluno) {
                const disponibilidadeEncontrada =
                    buscarDisponibilidade(dataFormatada);

                const disponibilidade =
                    disponibilidadeEncontrada || {
                        data: dataFormatada,
                        horas: 0
                    };

                const marcadorDisponibilidade =
                    criarMarcadorDisponibilidade(disponibilidade);

                celula.classList.add('tem-disponibilidade');

                celula.appendChild(marcadorDisponibilidade);
            }

            celula.appendChild(numeroDia);
            celula.appendChild(linkCadastro);

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
            const partes = this.value.split('-');

            anoAtual = Number(partes[0]);
            mesAtual = Number(partes[1]) - 1;

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
            const estaOculto =
                listaEventos.classList.toggle('cards-ocultos');

            this.textContent =
                estaOculto
                    ? 'Mostrar cards'
                    : 'Ocultar cards';

            this.setAttribute(
                'aria-expanded',
                String(!estaOculto)
            );
        });
    }

    if (seletorOrdenacao) {
        seletorOrdenacao.addEventListener('change', function () {
            atualizarVisaoEventosDoMes();
        });
    }

    document.querySelectorAll('.form-excluir-evento').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const confirmar =
                confirm('Deseja realmente excluir este evento?');

            if (!confirmar) {
                event.preventDefault();
            }
        });
    });

    atualizarCalendario();
});