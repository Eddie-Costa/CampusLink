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

// O mesmo modal permite consultar os termos no rodapé e nos cadastros.
(() => {
    const modal = document.getElementById("modalTermos");
    const destino = document.getElementById("textoTermos");
    if (!modal || !destino) return;

    let carregados = false;
    let carregando = false;

    modal.addEventListener("show.bs.modal", async () => {
        if (carregados || carregando) return;

        carregando = true;
        destino.textContent = "Carregando termos...";
        const controlador = new AbortController();
        const limite = setTimeout(() => controlador.abort(), 15000);

        try {
            const resposta = await fetch(destino.dataset.url, { signal: controlador.signal });
            if (!resposta.ok || resposta.redirected ||
                !resposta.headers.get("Content-Type")?.toLowerCase().startsWith("text/plain")) {
                throw new Error("Não foi possível obter o documento de termos.");
            }

            const texto = await resposta.text();
            if (!texto.trim()) throw new Error("O documento de termos está vazio.");

            renderizarTermos(destino, texto);
            carregados = true;
            modal.dispatchEvent(new Event("termos:carregados"));
        } catch {
            destino.textContent =
                "Não foi possível carregar os termos. Feche e abra esta janela para tentar novamente.";
        } finally {
            clearTimeout(limite);
            carregando = false;
        }
    });
})();
