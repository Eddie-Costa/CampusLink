// termos de uso carrega o documento e apresenta seu texto em secoes listas e tabelas


// renderizar termos transforma o texto do documento em titulos paragrafos listas e tabelas
function renderizarTermos(destino, texto) {
    const documento = document.createDocumentFragment();
    const blocos = texto.replace(/\r\n?/g, "\n").trim().split(/\n\s*\n/);

    // criar elemento dos termos insere o conteudo como texto e aplica a classe quando informada
    function elemento(tag, conteudo, classe) {
        const node = document.createElement(tag);
        node.textContent = conteudo;
        // aplicar estilo dos termos define a classe quando foi informada
        if (classe) node.className = classe;
        return node;
    }

    // organizar bloco dos termos identifica tabelas listas titulos secoes e dados de versao
    blocos.forEach((bloco, indice) => {
        // limpar linha dos termos remove os espacos das extremidades
        const linhas = bloco.split("\n").map(linha => linha.trim());

        // identificar linha de tabela verifica se a linha usa o separador de colunas
        // montar tabela dos termos transforma linhas com separadores em cabecalho e celulas
        if (linhas.length > 1 && linhas.every(linha => linha.includes(" | "))) {
            const tabela = document.createElement("table");
            const cabecalhos = linhas[0].split(" | ");
            const thead = document.createElement("thead");
            const tr = document.createElement("tr");
            // montar cabecalho dos termos cria o titulo de cada coluna da tabela
            cabecalhos.forEach(titulo => {
                const th = elemento("th", titulo);
                th.scope = "col";
                tr.append(th);
            });
            thead.append(tr);
            tabela.append(thead);

            const tbody = document.createElement("tbody");
            // montar linha dos termos separa os valores e adiciona a linha ao corpo da tabela
            linhas.slice(1).forEach(linha => {
                const tr = document.createElement("tr");
                // montar celula dos termos insere o valor e associa o nome da coluna
                linha.split(" | ").forEach((valor, coluna) => {
                    const td = elemento("td", valor);
                    td.dataset.label = cabecalhos[coluna] || "";
                    tr.append(td);
                });
                tbody.append(tr);
            });
            tabela.append(tbody);
            documento.append(tabela);
        // identificar item de lista confere se a linha comeca com a marca de item
        }
        // montar lista dos termos transforma linhas marcadas com asterisco em itens de lista
        else if (linhas.every(linha => /^\*\s+/.test(linha))) {
            const lista = document.createElement("ul");
            // montar item dos termos remove a marca inicial e adiciona o texto a lista
            linhas.forEach(linha => lista.append(elemento("li", linha.replace(/^\*\s+/, ""))));
            documento.append(lista);
        }
        // mostrar titulo dos termos usa o primeiro bloco como titulo principal
        else if (indice === 0) {
            documento.append(elemento("h3", bloco, "termos-titulo"));
        }
        // mostrar secao dos termos usa um bloco numerado de uma linha como titulo de secao
        else if (/^\d+\.\s+/.test(bloco) && linhas.length === 1) {
            documento.append(elemento("h4", bloco, "termos-secao"));
        }
        // mostrar versao dos termos destaca os dados de versao e atualizacao do documento
        else if (/^(Versão:|Última atualização:)/.test(bloco)) {
            documento.append(elemento("p", bloco, "termos-metadados"));
        } else {
            documento.append(elemento("p", bloco));
        }
    });


    destino.replaceChildren(documento);
}


// preparar janela de termos configura a carga do documento quando a janela abre
(() => {
    const modal = document.getElementById("modalTermos");
    const destino = document.getElementById("textoTermos");
    // configurar janela dos termos exige a janela e a area de texto
    if (!modal || !destino) return;

    let carregados = false;
    let carregando = false;

    // buscar termos da janela valida a resposta exibe o documento e avisa que os termos foram carregados
    modal.addEventListener("show.bs.modal", async () => {
        // evitar carregamento repetido dos termos ignora pedidos ja atendidos ou em andamento
        if (carregados || carregando) return;

        carregando = true;
        destino.textContent = "Carregando termos...";
        const controlador = new AbortController();
        // limitar consulta dos termos interrompe a busca depois de quinze segundos
        const limite = setTimeout(() => controlador.abort(), 15000);

        // consultar documento dos termos tenta carregar e apresentar o texto e avisar a conclusao
        try {
            const resposta = await fetch(destino.dataset.url, { signal: controlador.signal });
            // validar documento dos termos rejeita falhas redirecionamentos e respostas que nao sao texto
            if (!resposta.ok || resposta.redirected ||
                !resposta.headers.get("Content-Type")?.toLowerCase().startsWith("text/plain")) {
                throw new Error("Não foi possível obter o documento de termos.");
            }

            const texto = await resposta.text();
            // validar documento dos termos rejeita texto vazio
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
