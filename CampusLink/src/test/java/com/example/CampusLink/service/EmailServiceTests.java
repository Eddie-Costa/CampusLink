// verificacao de emails exercita a exportacao dos dados pessoais sem envio real
package com.example.CampusLink.service;

import com.example.CampusLink.repository.UsuarioRepository;
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
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// testes das mensagens esta classe confere destinatario formatacao protecao de texto e falhas de envio
class EmailServiceTests {
    private emailService emails;
    private JavaMailSender remetente;
    private MimeMessage mensagem;
    private DadosUsuarioDTO dados;

    // preparar teste de emails este metodo configura envio simulado modelos e dados pessoais de exemplo
    @BeforeEach
    void preparar() {
        emails = new emailService();
        remetente = mock(JavaMailSender.class);
        mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(remetente.createMimeMessage()).thenReturn(mensagem);
        ReflectionTestUtils.setField(emails, "mailSender", remetente);
        ReflectionTestUtils.setField(emails, "UsuarioRepository", mock(UsuarioRepository.class));
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        ReflectionTestUtils.setField(emails, "emailTemplateService", new EmailTemplateService(templateEngine));
        dados = new DadosUsuarioDTO();
        dados.setNome("Ana <img src=x> & Silva");
        dados.setEmail("ana@example.com");
        dados.setTelefone("11999990000");
        dados.setDataNascimento(LocalDate.of(2001, 2, 3));
        dados.setPerfil("ALUNOS");
        dados.setIdentificador("123456");
        dados.setDataCadastro(LocalDate.of(2026, 9, 1));
    }

    // testar exportacao em email este teste confere os dados nas duas versoes e protege o texto inserido na mensagem
    @Test
    void enviaDadosEmHtmlETextoComCaracteresEscapados() throws Exception {
        emails.enviarEmailDados(dados);
        mensagem.saveChanges();

        verify(remetente).send(mensagem);
        assertEquals("ana@example.com", mensagem.getAllRecipients()[0].toString());
        assertEquals("CampusLink | Seus dados pessoais", mensagem.getSubject());
        String html = conteudo(mensagem, "text/html");
        String texto = conteudo(mensagem, "text/plain");
        assertTrue(html.contains("Ana &lt;img src=x&gt; &amp; Silva"));
        assertFalse(html.contains("<img src=x>"));
        assertTrue(texto.contains(dados.getNome()));
        for (String valor : new String[]{"ana@example.com", "11999990000", "03/02/2001",
                "Aluno", "RGM", "123456", "01/09/2026"}) {
            assertTrue(html.contains(valor), valor);
            assertTrue(texto.contains(valor), valor);
        }
        assertTrue(html.contains("CampusLink"));
        assertTrue(html.contains("Termos de Uso"));
    }

    // testar email do professor este teste confere matricula e indicacao dos campos ausentes
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

    // testar falha no envio da exportacao este teste exige que o erro de email seja repassado ao chamador
    @Test
    void propagaFalhaDeEnvioParaNaoConfirmarExportacao() {
        MailSendException falha = new MailSendException("SMTP indisponível");
        doThrow(falha).when(remetente).send(mensagem);

        assertSame(falha, assertThrows(MailSendException.class, () -> emails.enviarEmailDados(dados)));
    }

    // buscar parte do email no teste este metodo percorre a mensagem ate encontrar o formato solicitado
    private String conteudo(Part parte, String tipo) throws Exception {
        // encontrar conteudo do email retorna a parte com o tipo procurado
        if (parte.isMimeType(tipo)) {
            return (String) parte.getContent();
        }
        // percorrer partes do email busca o conteudo dentro de mensagens com varias partes
        if (parte.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) parte.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String encontrado = conteudo(multipart.getBodyPart(i), tipo);
                // retornar parte do email encerra a busca quando encontra o conteudo procurado
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }
}
