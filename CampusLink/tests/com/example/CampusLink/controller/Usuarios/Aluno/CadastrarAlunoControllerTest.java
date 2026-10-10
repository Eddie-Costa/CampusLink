package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dto.Aluno.cadastrarAlunoDTO;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadastrarAlunoControllerTest {

    private final TwoFactorServiceSpy twoFactorService = new TwoFactorServiceSpy();

    @AfterEach
    void limparCodigoGerado() {
        if (twoFactorService.chaveLimpa != null) {
            twoFactorService.limparCodigo(twoFactorService.chaveLimpa);
        }
    }

    @Test
    void registrar_deveRejeitarFormularioInvalidoSemEnviarCodigo() {
        EmailServiceFake emailService = new EmailServiceFake();
        cadastrarAlunoController controller = new cadastrarAlunoController(twoFactorService, emailService);
        cadastrarAlunoDTO aluno = new cadastrarAlunoDTO();
        aluno.setEmail("email-invalido");
        aluno.setSenha("SenhaForte123!");
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(aluno, "aluno");
        resultado.rejectValue("email", "EmailInvalido", "Email inválido");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.registrar(aluno, resultado, model, session);

        assertEquals("Usuarios/Aluno/cadastrarAluno", view);
        assertNull(aluno.getSenha());
        assertTrue(model.containsAttribute("mensagemDeErro"));
        assertFalse(emailService.codigoEnviado);
        assertNull(session.getAttribute("cadastroPendente"));
    }

    @Test
    void registrar_deveLimparDadosTemporariosQuandoEmailFalhar() {
        EmailServiceFake emailService = new EmailServiceFake();
        emailService.falharAoEnviar = true;
        cadastrarAlunoController controller = new cadastrarAlunoController(twoFactorService, emailService);
        cadastrarAlunoDTO aluno = new cadastrarAlunoDTO();
        aluno.setEmail("aluno@campuslink.invalid");
        aluno.setSenha("SenhaForte123!");
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(aluno, "aluno");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.registrar(aluno, resultado, model, session);

        assertEquals("Usuarios/Aluno/cadastrarAluno", view);
        assertNull(aluno.getSenha());
        assertTrue(model.containsAttribute("mensagemDeErro"));
        assertNull(session.getAttribute("cadastroPendente"));
        assertNotNull(twoFactorService.chaveLimpa);
        assertEquals(0L, twoFactorService.obterExpiracaoCodigo(twoFactorService.chaveLimpa));
    }

    private static class EmailServiceFake extends emailService {
        private boolean falharAoEnviar;
        private boolean codigoEnviado;

        @Override
        public void enviarCodigo(String para, String codigo) {
            if (falharAoEnviar) {
                throw new IllegalStateException("Falha simulada no envio");
            }
            codigoEnviado = true;
        }
    }

    private static class TwoFactorServiceSpy extends TwoFactorService {
        private String chaveLimpa;

        @Override
        public void limparCodigo(String chave) {
            chaveLimpa = chave;
            super.limparCodigo(chave);
        }
    }
}
