document.addEventListener('DOMContentLoaded', function () {
    const selectModal = document.getElementById('selecionarConteudoModal');

    document.querySelectorAll('.conteudo-selecionar-item').forEach(function (button) {
        button.addEventListener('click', function () {
            const conteudoId = this.dataset.id;
            const editModal = document.getElementById('editarConteudoModal-' + conteudoId);

            if (selectModal && editModal) {
                const selectModalInstance = bootstrap.Modal.getOrCreateInstance(selectModal);
                const editModalInstance = bootstrap.Modal.getOrCreateInstance(editModal);

                selectModalInstance.hide();
                editModalInstance.show();
            }
        });
    });

    const paginaTurma = document.querySelector('.turmas-page');

    if (paginaTurma && paginaTurma.dataset.abrirModalParticipantes === 'true') {
        const modalElement = document.getElementById('participantesTurma');

        if (modalElement) {
            const modal = new bootstrap.Modal(modalElement);
            modal.show();
        }
    }

    const formExcluirTurma = document.querySelector('.form-excluir-turma');

    if (formExcluirTurma) {
        formExcluirTurma.addEventListener('submit', function (event) {
            const confirmar = confirm(
                'Tem certeza que deseja excluir esta turma? Isso removerá também todos os alunos e professores vinculados a ela.'
            );

            if (!confirmar) {
                event.preventDefault();
            }
        });
    }

    document.querySelectorAll('.conteudo-excluir-form').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const confirmar = confirm('Excluir este conteúdo? Essa ação não pode ser desfeita.');

            if (!confirmar) {
                event.preventDefault();
            }
        });
    });
});