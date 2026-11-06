// lista administrativa de turmas pesquisa os nomes e informa quando nao ha resultados
// preparar busca administrativa de turmas configura o filtro e oculta o aviso inicial
document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("buscarTurma");
    const linhasTurmas = document.querySelectorAll(".turma-linha");
    const mensagemVazio = document.getElementById("nenhumaTurmaEncontrada");

    // limpar aviso de turmas oculta a mensagem de lista vazia ao iniciar
    if (mensagemVazio) {
        mensagemVazio.style.display = "none";
    }

    // iniciar busca administrativa de turmas encerra quando o campo nao existe
    if (!campoBusca) {
        return;
    }

    // buscar turma administrativa percorre as linhas e informa quando nao encontra nomes
    campoBusca.addEventListener("input", function () {

        const busca = campoBusca.value.toLowerCase().trim();
        let turmasVisiveis = 0;

        // comparar turma administrativa mostra apenas os nomes que contem a busca
        linhasTurmas.forEach(function (linha) {

            const nomeTurma = linha.getAttribute("data-nome").toLowerCase();
            const encontrou = nomeTurma.includes(busca);

            // filtrar linhas de turmas mostra e conta as turmas encontradas ou oculta as demais
            if (encontrou) {
                linha.style.display = "";
                turmasVisiveis++;
            } else {
                linha.style.display = "none";
            }
        });

        // avisar turma nao encontrada exibe a mensagem quando nenhuma turma corresponde
        if (mensagemVazio) {
            mensagemVazio.style.display = turmasVisiveis === 0 ? "" : "none";
        }
    });
});