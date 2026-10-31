const inputBusca = document.getElementById('buscaTurma');
if (inputBusca) {
    const sugestoes = document.getElementById('sugestoesTurma');
    const buscaContainer = inputBusca.closest('.busca-turma-container');
    const cartoes = Array.from(document.querySelectorAll('#listaTurmas .turma-card'));
    const normalizar = texto => texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

    function fecharSugestoes() {
        sugestoes.replaceChildren();
        sugestoes.hidden = true;
        inputBusca.setAttribute('aria-expanded', 'false');
    }

    inputBusca.addEventListener('input', () => {
        const termo = normalizar(inputBusca.value.trim());
        const correspondencias = [];

        cartoes.forEach(card => {
            const nome = card.querySelector('.turma-nome').textContent.trim();
            const corresponde = normalizar(nome).includes(termo);
            card.style.display = corresponde ? '' : 'none';
            if (termo && corresponde) correspondencias.push({ nome, href: card.href });
        });

        sugestoes.replaceChildren();
        if (!termo) {
            sugestoes.hidden = true;
        } else if (correspondencias.length) {
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

    document.addEventListener('click', evento => {
        if (!buscaContainer.contains(evento.target)) fecharSugestoes();
    });
}
