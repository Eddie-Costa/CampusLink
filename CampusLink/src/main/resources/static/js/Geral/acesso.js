// telas de acesso controlam mostrar senha mascaras requisitos da senha e a escolha de perfil do cadastro

const REGRAS_SENHA = {
    tamanho: valor => valor.length >= 8,
    maiuscula: valor => /[A-Z]/.test(valor),
    minuscula: valor => /[a-z]/.test(valor),
    numero: valor => /\d/.test(valor),
    especial: valor => /[@#$%^&+=!]/.test(valor)
};

document.addEventListener("DOMContentLoaded", () => {
    configurarMostrarSenha();
    configurarSomenteNumeros();
    configurarRequisitosSenha();
    configurarCadastro();
});

// mostrar senha este metodo alterna o campo entre texto e senha e informa o estado para leitores de tela
function configurarMostrarSenha() {
    document.querySelectorAll("[data-mostrar-senha]").forEach(botao => {
        const campo = document.getElementById(botao.dataset.mostrarSenha);
        // exigir campo da senha ignora o botao quando o campo nao existe
        if (!campo) {
            return;
        }
        botao.addEventListener("click", () => {
            const mostrando = campo.type === "text";
            campo.type = mostrando ? "password" : "text";
            botao.setAttribute("aria-pressed", String(!mostrando));
            botao.setAttribute("aria-label", mostrando ? "Mostrar senha" : "Ocultar senha");
            campo.focus();
        });
    });
}

// aceitar so numeros este metodo remove letras dos campos de rgm e matricula e atualiza o contador de digitos
function configurarSomenteNumeros() {
    document.querySelectorAll("[data-somente-numeros]").forEach(campo => {
        const contador = document.querySelector(`[data-contador="${campo.id}"]`);
        const atualizar = () => {
            campo.value = campo.value.replace(/\D/g, "").slice(0, Number(campo.maxLength) || undefined);
            // mostrar progresso informa quantos digitos faltam ate completar o identificador
            if (contador) {
                const total = Number(contador.dataset.total);
                const faltam = total - campo.value.length;
                contador.textContent = faltam > 0 ? `${campo.value.length} de ${total} números` : `${total} números ✓`;
            }
        };
        campo.addEventListener("input", atualizar);
        atualizar();
    });
}

// mostrar requisitos da senha este metodo marca cada regra cumprida enquanto o usuario digita
function configurarRequisitosSenha() {
    document.querySelectorAll("[data-requisitos-senha]").forEach(lista => {
        const campo = document.getElementById(lista.dataset.requisitosSenha);
        // exigir campo da senha ignora a lista quando o campo nao existe
        if (!campo) {
            return;
        }
        const forca = document.querySelector(`[data-forca-senha="${campo.id}"]`);
        const atualizar = () => {
            let todas = true;
            let cumpridas = 0;
            lista.querySelectorAll("[data-regra]").forEach(item => {
                const cumprida = REGRAS_SENHA[item.dataset.regra](campo.value);
                item.classList.toggle("cumprido", cumprida);
                todas = todas && cumprida;
                cumpridas += cumprida ? 1 : 0;
            });
            // mostrar forca da senha cresce a barra conforme a quantidade de regras cumpridas
            if (forca) {
                forca.dataset.nivel = String(cumpridas);
                forca.firstElementChild.style.width = `${cumpridas * 20}%`;
            }
            campo.setCustomValidity(todas || campo.value === "" ? "" : "A senha ainda não cumpre todos os requisitos.");
        };
        campo.addEventListener("input", atualizar);
        atualizar();
    });
}

// controlar cadastro este metodo troca o perfil carrega os termos e libera o botao quando tudo esta pronto
function configurarCadastro() {
    const formulario = document.getElementById("formCadastro");
    // exigir formulario de cadastro ignora a configuracao nas outras telas
    if (!formulario) {
        return;
    }
    const botao = document.getElementById("btnCadastrar");
    const aceite = document.getElementById("termos");
    const orientacao = document.getElementById("orientacaoTermos");
    const textoTermos = document.getElementById("textoTermos");
    const modalTermos = document.getElementById("modalTermos");
    let termosCarregados = false;
    let carregandoTermos = false;

    // trocar perfil mostra rgm ou matricula e envia o formulario para o cadastro do perfil escolhido
    const aplicarPerfil = perfil => {
        formulario.action = perfil === "professor" ? formulario.dataset.acaoProfessor : formulario.dataset.acaoAluno;
        formulario.querySelectorAll("[data-perfil-campo]").forEach(bloco => {
            const ativo = bloco.dataset.perfilCampo === perfil;
            bloco.hidden = !ativo;
            bloco.querySelectorAll("input").forEach(campo => {
                campo.disabled = !ativo;
            });
        });
        validar();
    };

    // liberar envio exige campos validos e termos lidos e aceitos
    const validar = () => {
        const camposValidos = Array.from(formulario.querySelectorAll("input[required]:not([disabled]):not(#termos)"))
            .every(campo => campo.value.trim() !== "" && campo.checkValidity());
        botao.disabled = !(camposValidos && termosCarregados && aceite.checked);
    };

    // carregar termos busca o documento e so libera o aceite depois que ele foi exibido
    const carregarTermos = async () => {
        // evitar nova carga ignora o pedido quando os termos ja chegaram ou estao chegando
        if (termosCarregados || carregandoTermos) {
            return;
        }
        carregandoTermos = true;
        textoTermos.textContent = "Carregando termos...";
        const controlador = new AbortController();
        const limite = setTimeout(() => controlador.abort(), 15000);
        // buscar termos tenta ler o documento e mostra um aviso quando nao consegue
        try {
            const resposta = await fetch(textoTermos.dataset.url, { signal: controlador.signal });
            // validar resposta recusa falhas redirecionamentos e respostas que nao sao texto
            if (!resposta.ok || resposta.redirected
                || !resposta.headers.get("Content-Type")?.toLowerCase().startsWith("text/plain")) {
                throw new Error("Não foi possível obter os termos.");
            }
            const texto = await resposta.text();
            // validar conteudo recusa um documento vazio
            if (!texto.trim()) {
                throw new Error("Os termos estão vazios.");
            }
            renderizarTermos(textoTermos, texto);
            termosCarregados = true;
            aceite.disabled = false;
            orientacao.textContent = "Depois de ler, marque o aceite para continuar.";
        } catch {
            textoTermos.textContent = "Não foi possível carregar os termos. Feche e abra esta janela para tentar novamente.";
        } finally {
            clearTimeout(limite);
            carregandoTermos = false;
            validar();
        }
    };

    formulario.querySelectorAll("input[name='perfilCadastro']").forEach(opcao => {
        opcao.addEventListener("change", () => aplicarPerfil(opcao.value));
    });
    formulario.addEventListener("input", validar);
    aceite.addEventListener("change", validar);
    modalTermos?.addEventListener("show.bs.modal", carregarTermos);

    const escolhido = formulario.querySelector("input[name='perfilCadastro']:checked");
    aplicarPerfil(escolhido ? escolhido.value : "aluno");
}
