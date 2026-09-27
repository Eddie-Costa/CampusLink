document.addEventListener('DOMContentLoaded', function () {
    const campoBusca = document.getElementById('campoBusca');
    const filtroStatus = document.getElementById('filtroStatus');
    const conteudos = document.querySelectorAll('.conteudo-card');
    const semResultado = document.getElementById('semResultadoBusca');

    function filtrarConteudos() {
        const busca = campoBusca.value.toLowerCase().trim();
        const statusSelecionado = filtroStatus.value;

        let quantidadeVisivel = 0;

        conteudos.forEach(function (conteudo) {
            const titulo = (conteudo.dataset.titulo || '').toLowerCase();
            const turma = (conteudo.dataset.turma || '').toLowerCase();
            const status = (conteudo.dataset.status || '').toLowerCase();

            const combinaBusca =
                titulo.includes(busca) ||
                turma.includes(busca) ||
                status.includes(busca);

            const combinaStatus =
                statusSelecionado === 'todos' ||
                status === statusSelecionado;

            if (combinaBusca && combinaStatus) {
                conteudo.classList.remove('d-none');
                quantidadeVisivel++;
            } else {
                conteudo.classList.add('d-none');
            }
        });

        if (quantidadeVisivel === 0 && conteudos.length > 0) {
            semResultado.classList.remove('d-none');
        } else {
            semResultado.classList.add('d-none');
        }
    }

    campoBusca.addEventListener('input', filtrarConteudos);
    filtroStatus.addEventListener('change', filtrarConteudos);

    document.querySelectorAll('.form-remover-conteudo').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const confirmar = confirm('Tem certeza que deseja remover este conteúdo?');

            if (!confirmar) {
                event.preventDefault();
            }
        });
    });
});