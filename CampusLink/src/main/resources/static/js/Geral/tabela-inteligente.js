// tabela inteligente ordena colunas ao clicar no cabecalho e transforma linhas em cartoes no celular
// preparar tabelas configura todas as tabelas com cabecalho dentro do conteudo principal
document.addEventListener('DOMContentLoaded', function () {
    const tabelas = Array.from(document.querySelectorAll('main table')).filter(function (tabela) {
        return tabela.tHead && tabela.tBodies.length > 0;
    });

    // ignorar pagina sem tabela encerra sem carregar o estilo
    if (tabelas.length === 0) {
        return;
    }

    // carregar estilo da tabela inclui o css uma unica vez para nao exigir alteracao no head
    if (!document.querySelector('link[data-tabela-inteligente]')) {
        const estilo = document.createElement('link');

        estilo.rel = 'stylesheet';
        estilo.href = '/css/Geral/tabela-inteligente.css?v=20261112-4';
        estilo.setAttribute('data-tabela-inteligente', '');
        document.head.appendChild(estilo);
    }

    const comparador = new Intl.Collator('pt-BR', { numeric: true, sensitivity: 'base' });

    // ler valor da celula transforma datas e numeros em valores comparaveis
    function lerValor(celula) {
        const texto = (celula ? celula.textContent : '').replace(/\s+/g, ' ').trim();
        const data = texto.match(/^(\d{2})\/(\d{2})\/(\d{4})(?:\s+(\d{2}):(\d{2}))?/);

        // converter data brasileira usa ano mes dia e hora para ordenar cronologicamente
        if (data) {
            return { tipo: 'numero', valor: Number(data[3] + data[2] + data[1] + (data[4] || '00') + (data[5] || '00')) };
        }

        const numero = texto.replace(/\./g, '').replace(',', '.');

        // converter numero puro compara quantidades pelo valor e nao pelo texto
        if (numero !== '' && /^-?\d+(\.\d+)?$/.test(numero)) {
            return { tipo: 'numero', valor: Number(numero) };
        }

        return { tipo: 'texto', valor: texto };
    }

    // comparar valores ordena numeros entre si e textos pela regra do portugues
    function comparar(a, b) {
        // comparar numeros usa subtracao quando os dois lados sao numericos
        if (a.tipo === 'numero' && b.tipo === 'numero') {
            return a.valor - b.valor;
        }

        return comparador.compare(String(a.valor), String(b.valor));
    }

    tabelas.forEach(function (tabela) {
        const cabecalhos = Array.from(tabela.tHead.rows[0] ? tabela.tHead.rows[0].cells : []);
        const corpo = tabela.tBodies[0];
        const quantidadeColunas = cabecalhos.length;

        // identificar linhas de dados ignora mensagens de lista vazia que ocupam a tabela inteira
        function linhasDeDados() {
            return Array.from(corpo.rows).filter(function (linha) {
                return linha.cells.length === quantidadeColunas && !linha.id;
            });
        }

        tabela.classList.add('tabela-cartoes');

        // rotular celulas copia o nome da coluna para cada celula usada no modo cartao
        linhasDeDados().forEach(function (linha) {
            Array.from(linha.cells).forEach(function (celula, indice) {
                const titulo = cabecalhos[indice] ? cabecalhos[indice].textContent.trim() : '';

                // preencher rotulo mantem o rotulo que a pagina ja tiver definido
                if (titulo && !celula.hasAttribute('data-rotulo')) {
                    celula.setAttribute('data-rotulo', titulo);
                }            });
        });

        // ignorar ordenacao em tabela com uma linha evita botoes sem utilidade
        if (linhasDeDados().length < 2) {
            return;
        }

        cabecalhos.forEach(function (cabecalho, indice) {
            const titulo = cabecalho.textContent.trim();

            // ignorar colunas de acao mantem sem ordenacao as colunas de botoes ou vazias
            if (!titulo || /^a[cç][oõ]es$/i.test(titulo) || cabecalho.hasAttribute('data-sem-ordem')) {
                return;
            }

            const botao = document.createElement('button');

            botao.type = 'button';
            botao.className = 'tabela-ordenar';
            botao.innerHTML = '<span></span><span class="tabela-ordenar-seta" aria-hidden="true"></span>';
            botao.firstChild.textContent = titulo;
            botao.setAttribute('aria-label', `Ordenar por ${titulo}`);

            cabecalho.textContent = '';
            cabecalho.appendChild(botao);
            cabecalho.setAttribute('aria-sort', 'none');

            // ordenar coluna alterna entre crescente e decrescente a cada clique
            botao.addEventListener('click', function () {
                const crescente = cabecalho.getAttribute('aria-sort') !== 'ascending';
                const linhas = linhasDeDados();
                const extras = Array.from(corpo.rows).filter(function (linha) {
                    return linhas.indexOf(linha) === -1;
                });

                linhas.sort(function (linhaA, linhaB) {
                    const resultado = comparar(lerValor(linhaA.cells[indice]), lerValor(linhaB.cells[indice]));

                    return crescente ? resultado : -resultado;
                });

                cabecalhos.forEach(function (outro) {
                    // limpar outras colunas deixa apenas a coluna clicada marcada como ordenada
                    if (outro.hasAttribute('aria-sort')) {
                        outro.setAttribute('aria-sort', 'none');
                    }
                });

                cabecalho.setAttribute('aria-sort', crescente ? 'ascending' : 'descending');

                linhas.concat(extras).forEach(function (linha) {
                    corpo.appendChild(linha);
                });
            });
        });
    });
});
