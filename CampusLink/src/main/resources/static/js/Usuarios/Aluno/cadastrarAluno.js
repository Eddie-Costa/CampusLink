// cadastro do aluno carrega os termos e exige preenchimento e aceite para continuar
const campos = document.querySelectorAll("#rgm, #nome, #email, #telefone, #datanasc, #senha");
const checkbox = document.getElementById("termos");
const botao = document.getElementById("btnSubmit");
const textoTermos = document.getElementById("textoTermos");

let termosCarregados = false;
let carregandoTermos = false;

// carregar termos do aluno busca o documento e libera o aceite somente depois da leitura da resposta
async function carregarTermos() {
    // evitar recarga dos termos do aluno ignora pedidos quando os termos ja chegaram ou estao carregando
    if (termosCarregados || carregandoTermos) {
        return;
    }

    carregandoTermos = true;
    checkbox.disabled = true;
    checkbox.checked = false;

    validarCampos();

    textoTermos.textContent = "Carregando termos...";

    const controlador = new AbortController();
    // limitar espera dos termos do aluno cancela a consulta depois de quinze segundos
    const limite = setTimeout(() => controlador.abort(), 15000);

    // carregar termos do aluno tenta buscar o documento e liberar o aceite apos a leitura
    try {
        const resposta = await fetch(textoTermos.dataset.url, {
            signal: controlador.signal
        });

        // validar documento dos termos rejeita falhas redirecionamentos e respostas que nao sao texto
        if (!resposta.ok || resposta.redirected ||
            !resposta.headers.get("Content-Type")?.toLowerCase().startsWith("text/plain")) {
            throw new Error("Não foi possível obter o documento de termos.");
        }

        const texto = await resposta.text();

        // validar termos do aluno rejeita um documento vazio
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

// validar cadastro do aluno exige campos preenchidos e aceite dos termos carregados
function validarCampos() {
    let todosPreenchidos = true;

    // conferir campo do aluno identifica valores vazios no formulario
    campos.forEach(campo => {
        // validar cadastro do aluno marca o formulario como incompleto quando existe campo vazio
        if (campo.value.trim() === "") {
            todosPreenchidos = false;
        }
    });

    const termosAceitos = termosCarregados && !checkbox.disabled && checkbox.checked;

    botao.disabled = !(todosPreenchidos && termosAceitos);
}

// acompanhar cadastro do aluno revalida os campos durante a digitacao
campos.forEach(campo => {
    campo.addEventListener("input", validarCampos);
});

checkbox.addEventListener("change", validarCampos);

document.getElementById("modalTermos").addEventListener("show.bs.modal", carregarTermos);

validarCampos();