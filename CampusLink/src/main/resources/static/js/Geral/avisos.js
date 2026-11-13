// avisos padronizados mostram mensagens de sucesso e erro como toast no canto da tela em todas as paginas
(function () {
    const tempoSucesso = 5000;
    let area = null;

    // garantir area dos avisos cria uma unica pilha fixa para todos os toasts
    function obterArea() {
        // reaproveitar area existente evita criar varias pilhas na mesma pagina
        if (area && document.body.contains(area)) return area;
        area = document.createElement('div');
        area.className = 'aviso-area';
        area.setAttribute('aria-live', 'polite');
        document.body.appendChild(area);
        return area;
    }

    // icone do aviso desenha o simbolo de sucesso erro ou alerta
    function icone(tipo) {
        const caminhos = {
            sucesso: '<path d="M20 6 9 17l-5-5"/>',
            erro: '<path d="M12 8v5"/><path d="M12 16.5v.5"/><circle cx="12" cy="12" r="9"/>',
            alerta: '<path d="M12 9v4"/><path d="M12 17v.01"/><path d="M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>'
        };
        return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${caminhos[tipo] || caminhos.alerta}</svg>`;
    }

    // mostrar aviso cria o toast com texto botao de fechar e saida automatica para sucesso
    function mostrar(texto, tipo = 'sucesso') {
        const mensagem = String(texto || '').trim();
        // ignorar aviso vazio evita toasts sem conteudo
        if (!mensagem) return null;
        const aviso = document.createElement('div');
        aviso.className = `aviso aviso-${tipo}`;
        aviso.setAttribute('role', tipo === 'sucesso' ? 'status' : 'alert');
        aviso.innerHTML = `<span class="aviso-icone">${icone(tipo)}</span><p class="aviso-texto"></p><button type="button" class="aviso-fechar" aria-label="Fechar aviso">×</button>`;
        aviso.querySelector('.aviso-texto').textContent = mensagem;

        let temporizador = null;
        // fechar aviso anima a saida e remove o toast da pilha
        const fechar = () => {
            clearTimeout(temporizador);
            aviso.classList.add('aviso-saindo');
            setTimeout(() => aviso.remove(), 250);
        };
        aviso.querySelector('.aviso-fechar').addEventListener('click', fechar);

        // agendar saida fecha sozinho apenas os avisos de sucesso
        if (tipo === 'sucesso') {
            aviso.style.setProperty('--aviso-tempo', `${tempoSucesso}ms`);
            aviso.classList.add('aviso-temporario');
            const iniciar = () => { temporizador = setTimeout(fechar, tempoSucesso); };
            // pausar no mouse da tempo de leitura enquanto o usuario aponta para o aviso
            aviso.addEventListener('mouseenter', () => { clearTimeout(temporizador); aviso.classList.add('aviso-pausado'); });
            aviso.addEventListener('mouseleave', () => { aviso.classList.remove('aviso-pausado'); iniciar(); });
            iniciar();
        }

        obterArea().appendChild(aviso);
        return aviso;
    }

    // descobrir tipo do aviso le as classes do alerta original
    function tipoDe(elemento) {
        // identificar sucesso reconhece alertas verdes e o popup do calendario
        if (elemento.matches('.alert-success, .popup-sucesso')) return 'sucesso';
        if (elemento.matches('.alert-danger')) return 'erro';
        return 'alerta';
    }

    // converter mensagens da pagina troca os alertas de retorno do servidor pelos toasts padronizados
    function converterMensagens() {
        const seletores = [
            '.alerta-flutuante', '.popup-sucesso', '.mensagem-pagina',
            '.alert.alert-success[class*="-admin-mensagem"]', '.alert.alert-danger[class*="-admin-mensagem"]',
            '.profile-page > .alert-success', '.profile-page > .alert-danger',
            'main .alert.alert-success'
        ];
        document.querySelectorAll(seletores.join(',')).forEach(elemento => {
            // manter mensagens de formulario e modais preserva erros ligados a campos e alertas controlados por script
            if (elemento.closest('form, .modal, [hidden], .d-none') || elemento.classList.contains('d-none') || elemento.dataset.avisoConvertido) return;
            const textoBase = elemento.querySelector('span') || elemento;
            const texto = textoBase.textContent.replace(/\s+/g, ' ').trim();
            elemento.dataset.avisoConvertido = 'sim';
            // remover original evita mostrar a mesma mensagem duas vezes
            if (mostrar(texto, tipoDe(elemento))) elemento.remove();
        });
    }

    window.campusAviso = mostrar;

    // iniciar avisos converte as mensagens assim que a pagina termina de carregar
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', converterMensagens);
    } else {
        converterMensagens();
    }
})();
