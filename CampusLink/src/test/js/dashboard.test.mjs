import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const codigo = readFileSync(new URL('../../main/resources/static/js/Geral/dashboard.js', import.meta.url), 'utf8');

export function testarGraficos() {
    function executar(valores, biblioteca = true, falha = false, tipo = 'bar') {
        const criados = [];
        const avisos = [];
        const detalhes = { open: true };
        const canvas = {};
        const container = { hidden: true, querySelector: () => canvas, after: aviso => avisos.push(aviso) };
        const linhas = valores.map((valor, indice) => ({
            querySelector: tag => ({ textContent: tag === 'td' ? String(valor) : `Categoria ${indice}` })
        }));
        const painel = {
            dataset: { tipo }, querySelectorAll: () => linhas,
            querySelector: seletor => seletor === 'details' ? detalhes : container
        };
        const contexto = {
            document: {
                addEventListener: (evento, callback) => callback(),
                querySelectorAll: () => [painel], createElement: () => ({})
            },
            window: { matchMedia: () => ({ matches: true }) }
        };
        if (biblioteca) contexto.Chart = function (elemento, config) {
            if (falha) throw new Error('Falha de carregamento');
            criados.push(config);
        };
        runInNewContext(codigo, contexto);
        return { criados, avisos, detalhes, container };
    }

    for (const tipo of ['bar', 'line', 'doughnut']) {
        const resultado = executar([2, 5, 0], true, false, tipo);
        assert.equal(resultado.criados.length, 1);
        assert.equal(resultado.criados[0].type, tipo);
        assert.deepEqual(Array.from(resultado.criados[0].data.datasets[0].data), [2, 5, 0]);
        assert.equal(resultado.criados[0].options.animation, false);
        assert.equal(resultado.container.hidden, false);
        assert.equal(resultado.detalhes.open, false);
    }
    const zeros = executar([0, 0]);
    assert.equal(zeros.criados.length, 0);
    assert.equal(zeros.avisos.length, 1);
    assert.equal(zeros.detalhes.open, true);
    const vazio = executar([]);
    assert.equal(vazio.criados.length, 0);
    assert.equal(vazio.container.hidden, true);
    for (const resultado of [executar([1], false), executar([1], true, true)]) {
        assert.equal(resultado.container.hidden, true);
        assert.equal(resultado.detalhes.open, true);
    }
    return '7 cenários de gráficos aprovados';
}

console.log(testarGraficos());
