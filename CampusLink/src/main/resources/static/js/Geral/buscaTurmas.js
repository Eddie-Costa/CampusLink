// busca de turmas filtra os cartoes pelo nome e apresenta links sugeridos
const inputBusca = document.getElementById('buscaTurma');
// configurar busca de turmas prepara o filtro dos cartoes e as sugestoes quando existe um campo
if (inputBusca) {
    const sugestoes = document.getElementById('sugestoesTurma');
    const buscaContainer = inputBusca.closest('.busca-turma-container');
    const cartoes = Array.from(document.querySelectorAll('#listaTurmas .turma-card'));
    // normalizar busca de turma remove acentos e converte o texto para minusculas
    const normalizar = texto => texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

    // fechar sugestoes de turmas limpa as opcoes e informa que a lista esta fechada
    function fecharSugestoes() {
        sugestoes.replaceChildren();
        sugestoes.hidden = true;
        inputBusca.setAttribute('aria-expanded', 'false');
    }

    // buscar turmas filtra os cartoes e monta as sugestoes pelo nome digitado
    inputBusca.addEventListener('input', () => {
        const termo = normalizar(inputBusca.value.trim());
        const correspondencias = [];

        // filtrar cartao de turma mostra os nomes correspondentes e guarda seus links
        cartoes.forEach(card => {
            const nome = card.querySelector('.turma-nome').textContent.trim();
            const corresponde = normalizar(nome).includes(termo);
            card.style.display = corresponde ? '' : 'none';
            // reunir turmas encontradas guarda o nome e o link das turmas que correspondem ao termo
            if (termo && corresponde) correspondencias.push({ nome, href: card.href });
        });

        sugestoes.replaceChildren();
        // ocultar sugestoes de turmas fecha a lista quando o termo esta vazio
        if (!termo) {
            sugestoes.hidden = true;
        }
        // mostrar turmas encontradas cria as opcoes ou informa que nenhuma turma corresponde
        else if (correspondencias.length) {
            // montar sugestao de turma cria um link com o nome e o endereco da turma
            correspondencias.forEach(({ nome, href }) => {
                const opcao = document.createElement('a');
                opcao.className = 'sugestao-turma';
                opcao.href = href;
                opcao.setAttribute('role', 'option');
                opcao.textContent = nome;
                sugestoes.appendChild(opcao);
            });
            sugestoes.hidden = false;
        } else {
            const vazio = document.createElement('span');
            vazio.className = 'sugestao-turma-vazia';
            vazio.textContent = 'Nenhuma turma encontrada';
            sugestoes.appendChild(vazio);
            sugestoes.hidden = false;
        }

        inputBusca.setAttribute('aria-expanded', String(!sugestoes.hidden));
    });

    // fechar busca de turmas oculta as sugestoes ao clicar fora da area
    document.addEventListener('click', evento => {
        // fechar busca de turmas oculta as sugestoes ao clicar fora do campo
        if (!buscaContainer.contains(evento.target)) fecharSugestoes();
    });
}
