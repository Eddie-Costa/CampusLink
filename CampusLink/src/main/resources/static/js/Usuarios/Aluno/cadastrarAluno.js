const campos = document.querySelectorAll("#rgm, #nome, #email, #telefone, #datanasc, #senha");
const checkbox = document.getElementById("termos");
const botao = document.getElementById("btnSubmit");
const textoTermos = document.getElementById("textoTermos");

let termosCarregados = false;
let carregandoTermos = false;

async function carregarTermos() {
    if (termosCarregados || carregandoTermos) {
        return;
    }

    carregandoTermos = true;
    checkbox.disabled = true;
    checkbox.checked = false;

    validarCampos();

    textoTermos.textContent = "Carregando termos...";

    const controlador = new AbortController();
    const limite = setTimeout(() => controlador.abort(), 15000);

    try {
        const resposta = await fetch(textoTermos.dataset.url, {
            signal: controlador.signal
        });

        if (!resposta.ok || resposta.redirected ||
            !resposta.headers.get("Content-Type")?.toLowerCase().startsWith("text/plain")) {
            throw new Error("Não foi possível obter o documento de termos.");
        }

        const texto = await resposta.text();

        if (!texto.trim()) {
            throw new Error("O documento de termos está vazio.");
        }

        renderizarTermos(textoTermos, texto);

        termosCarregados = true;
        checkbox.disabled = false;

        document.getElementById("orientacaoTermos").textContent =
            "Após ler os termos, marque a opção de aceite para continuar.";

    } catch {
        textoTermos.textContent =
            "Não foi possível carregar os termos. Feche e abra esta janela para tentar novamente.";

    } finally {
        clearTimeout(limite);

        carregandoTermos = false;

        validarCampos();
    }
}

function validarCampos() {
    let todosPreenchidos = true;

    campos.forEach(campo => {
        if (campo.value.trim() === "") {
            todosPreenchidos = false;
        }
    });

    const termosAceitos = termosCarregados && !checkbox.disabled && checkbox.checked;

    botao.disabled = !(todosPreenchidos && termosAceitos);
}

campos.forEach(campo => {
    campo.addEventListener("input", validarCampos);
});

checkbox.addEventListener("change", validarCampos);

document.getElementById("modalTermos").addEventListener("show.bs.modal", carregarTermos);

validarCampos();