document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.form-excluir-arquivo').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const confirmar = confirm('Deseja realmente excluir este material?');

            if (!confirmar) {
                event.preventDefault();
            }
        });
    });
});