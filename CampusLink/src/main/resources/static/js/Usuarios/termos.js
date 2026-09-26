// TXT: parágrafos separados por linhas em branco, títulos "1. ...",
// listas com "* " e tabelas com colunas separadas por " | ".
function renderizarTermos(destino, texto) {
    const documento = document.createDocumentFragment();
    const blocos = texto.replace(/\r\n?/g, "\n").trim().split(/\n\s*\n/);

    function elemento(tag, conteudo, classe) {
        const node = document.createElement(tag);
        node.textContent = conteudo;
        if (classe) node.className = classe;
        return node;
    }

    blocos.forEach((bloco, indice) => {
        const linhas = bloco.split("\n").map(linha => linha.trim());

        if (linhas.length > 1 && linhas.every(linha => linha.includes(" | "))) {
            const tabela = document.createElement("table");
            const cabecalhos = linhas[0].split(" | ");
            const thead = document.createElement("thead");
            const tr = document.createElement("tr");
            cabecalhos.forEach(titulo => {
                const th = elemento("th", titulo);
                th.scope = "col";
                tr.append(th);
            });
            thead.append(tr);
            tabela.append(thead);

            const tbody = document.createElement("tbody");
            linhas.slice(1).forEach(linha => {
                const tr = document.createElement("tr");
                linha.split(" | ").forEach((valor, coluna) => {
                    const td = elemento("td", valor);
                    td.dataset.label = cabecalhos[coluna] || "";
                    tr.append(td);
                });
                tbody.append(tr);
            });
            tabela.append(tbody);
            documento.append(tabela);
        } else if (linhas.every(linha => /^\*\s+/.test(linha))) {
            const lista = document.createElement("ul");
            linhas.forEach(linha => lista.append(elemento("li", linha.replace(/^\*\s+/, ""))));
            documento.append(lista);
        } else if (indice === 0) {
            documento.append(elemento("h3", bloco, "termos-titulo"));
        } else if (/^\d+\.\s+/.test(bloco) && linhas.length === 1) {
            documento.append(elemento("h4", bloco, "termos-secao"));
        } else if (/^(Versão:|Última atualização:)/.test(bloco)) {
            documento.append(elemento("p", bloco, "termos-metadados"));
        } else {
            documento.append(elemento("p", bloco));
        }
    });

    // O documento é sempre tratado como texto, nunca como HTML executável.
    destino.replaceChildren(documento);
}
