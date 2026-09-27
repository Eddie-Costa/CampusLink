package com.example.CampusLink.service;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTests {
    private emailService emails;
    private JavaMailSender remetente;
    private MimeMessage mensagem;
    private DadosUsuarioDTO dados;

    @BeforeEach
    void preparar() {
        emails = new emailService();
        remetente = mock(JavaMailSender.class);
        mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(remetente.createMimeMessage()).thenReturn(mensagem);
        ReflectionTestUtils.setField(emails, "mailSender", remetente);
        ReflectionTestUtils.setField(emails, "usuarioDAO", mock(usuarioDAO.class));
        dados = new DadosUsuarioDTO();
        dados.setNome("Ana <img src=x> & Silva");
        dados.setEmail("ana@example.com");
        dados.setTelefone("11999990000");
        dados.setDataNascimento(LocalDate.of(2001, 2, 3));
        dados.setPerfil("ALUNOS");
        dados.setIdentificador("123456");
        dados.setDataCadastro(LocalDate.of(2026, 9, 1));
    }

    @Test
    void enviaDadosEmHtmlETextoComCaracteresEscapados() throws Exception {
        emails.enviarEmailDados(dados);
        mensagem.saveChanges();

        verify(remetente).send(mensagem);
        assertEquals("ana@example.com", mensagem.getAllRecipients()[0].toString());
        assertEquals("Dados pessoais - Exportação de dados", mensagem.getSubject());
        String html = conteudo(mensagem, "text/html");
        String texto = conteudo(mensagem, "text/plain");
        assertTrue(html.contains("Ana &lt;img src=x&gt; &amp; Silva"));
        assertFalse(html.contains("<img src=x>"));
        assertTrue(texto.contains(dados.getNome()));
        for (String valor : new String[]{"ana@example.com", "11999990000", "2001-02-03",
                "Aluno", "RGM", "123456", "2026-09-01"}) {
            assertTrue(html.contains(valor), valor);
            assertTrue(texto.contains(valor), valor);
        }
        assertTrue(html.contains("CampusLink"));
        assertTrue(html.contains("Termos de Uso"));
    }

    @Test
    void exportacaoDeProfessorExibeMatriculaECamposAusentes() throws Exception {
        dados.setPerfil("PROFESSORES");
        dados.setTelefone(null);
        dados.setDataNascimento(null);
        emails.enviarEmailDados(dados);
        mensagem.saveChanges();

        String html = conteudo(mensagem, "text/html");
        assertTrue(html.contains("Professor"));
        assertTrue(html.contains("Matrícula"));
        assertTrue(html.contains("Não informado"));
        assertFalse(html.contains(">null<"));
    }

    @Test
    void propagaFalhaDeEnvioParaNaoConfirmarExportacao() {
        MailSendException falha = new MailSendException("SMTP indisponível");
        doThrow(falha).when(remetente).send(mensagem);

        assertSame(falha, assertThrows(MailSendException.class, () -> emails.enviarEmailDados(dados)));
    }

    private String conteudo(Part parte, String tipo) throws Exception {
        if (parte.isMimeType(tipo)) {
            return (String) parte.getContent();
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) parte.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String encontrado = conteudo(multipart.getBodyPart(i), tipo);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }
}
