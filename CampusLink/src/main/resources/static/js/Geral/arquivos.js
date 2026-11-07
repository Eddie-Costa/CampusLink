// arquivos da turma pede confirmacao antes de excluir um material
// preparar exclusao de arquivos configura as confirmacoes ao carregar a pagina
document.addEventListener('DOMContentLoaded', function () {
    // configurar delete arquivo associa a confirmacao ao envio do formulario
    document.querySelectorAll('.form-excluir-arquivo').forEach(function (form) {
        // confirmar delete arquivo impede a exclusao quando a pessoa cancela
        form.addEventListener('submit', function (event) {
            const confirmar = confirm('Deseja realmente excluir este material?');

            // cancelar acao nao confirmada impede o envio quando a pessoa recusa a confirmacao
            if (!confirmar) {
                event.preventDefault();
            }
        });
    });
});