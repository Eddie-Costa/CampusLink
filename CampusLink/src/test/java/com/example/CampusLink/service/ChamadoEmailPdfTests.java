// verificacao do pdf e dos emails dos chamados confere historico paginas caracteres especiais anexo e falhas de envio
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import com.example.CampusLink.repository.UsuarioRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes do pdf e dos emails dos chamados esta classe gera pdfs reais e simula o servidor de email
class ChamadoEmailPdfTests {

    private static final OffsetDateTime ABERTURA = OffsetDateTime.of(2026, 11, 10, 13, 0, 0, 0, ZoneOffset.UTC);

    private ChamadoPdfService pdf;
    private JavaMailSender remetente;
    private MimeMessage mensagem;
    private emailService emails;

    // preparar testes dos chamados este metodo cria o gerador de pdf e o envio de emails com modelos reais
    @BeforeEach
    void preparar() {
        pdf = new ChamadoPdfService();
        remetente = mock(JavaMailSender.class);
        mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(remetente.createMimeMessage()).thenReturn(mensagem);
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine modelos = new SpringTemplateEngine();
        modelos.setTemplateResolver(resolver);
        emails = new emailService();
        ReflectionTestUtils.setField(emails, "mailSender", remetente);
        ReflectionTestUtils.setField(emails, "UsuarioRepository", mock(UsuarioRepository.class));
        ReflectionTestUtils.setField(emails, "emailTemplateService", new EmailTemplateService(modelos));
        ReflectionTestUtils.setField(emails, "chamadoPdfService", pdf);
    }

    // testar pdf do chamado este teste confere protocolo solicitante mensagens e autor sem identificacao
    @Test
    void geraPdfComHistoricoDoChamado() throws Exception {
        // preparar chamado concluido com uma resposta do administrador e uma do usuario
        ChamadoDTO chamado = concluido(List.of(resposta(1L, "admin", "Equipe resolveu"), resposta(2L, "usuario", "Obrigado")));
        chamado.setIdAdminConclusao(null);

        // executar geracao
        String texto = texto(pdf.gerar(chamado));

        // verificar dados principais do documento
        assertTrue(texto.contains("Chamado #15 | Concluído"));
        assertTrue(texto.contains("Solicitante: Ana (ID 2)"));
        assertTrue(texto.contains("Equipe resolveu"));
        assertTrue(texto.contains("Mensagem #2 | Solicitante"));
        assertTrue(texto.contains("conclusão: ID indisponível"));
    }

    // testar historico longo este teste confere a quebra de pagina a continuacao e o codigo dos caracteres sem fonte
    @Test
    void quebraPaginasERepresentaCaracteresSemFonte() throws Exception {
        // preparar chamado com mensagem longa e emoji
        String longa = "Linha com bastante texto para ocupar o espaco da pagina inteira. ".repeat(150);
        ChamadoDTO chamado = concluido(List.of(resposta(1L, "admin", longa + "\nFim 🙂\tcom tabulacao\r\nok")));

        // executar geracao
        byte[] arquivo = pdf.gerar(chamado);

        // verificar varias paginas continuacao rodape e codigo do emoji
        try (PDDocument documento = Loader.loadPDF(arquivo)) {
            String texto = new PDFTextStripper().getText(documento);
            assertTrue(documento.getNumberOfPages() > 1);
            assertTrue(texto.contains("Mensagem #1 (continuação)"));
            assertTrue(texto.contains("[U+1F642]"));
            assertTrue(texto.contains("Página 1 de " + documento.getNumberOfPages()));
            assertTrue(texto.contains("formato [U+XXXX]"));
        }
    }

    // testar chamado aberto este teste confere que o pdf so e gerado para chamado concluido
    @Test
    void recusaPdfDeChamadoNaoConcluido() {
        // preparar chamado aberto e chamado concluido sem data
        ChamadoDTO aberto = concluido(List.of());
        aberto.setStatus("aberto");
        ChamadoDTO semData = concluido(List.of());
        semData.setConcluidoEm(null);

        // executar geracoes
        MailPreparationException erroAberto = assertThrows(MailPreparationException.class, () -> pdf.gerar(aberto));
        MailPreparationException erroSemData = assertThrows(MailPreparationException.class, () -> pdf.gerar(semData));

        // verificar mensagem
        assertTrue(erroAberto.getMessage().contains("chamado concluído"));
        assertTrue(erroSemData.getMessage().contains("chamado concluído"));
    }

    // testar integridade do pdf este teste confere o hash sha256 conhecido de um conteudo fixo
    @Test
    void calculaHashDoPdf() {
        // preparar conteudo fixo
        byte[] conteudo = "abc".getBytes();

        // executar calculo
        String hash = pdf.sha256(conteudo);

        // verificar hash conhecido
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }

    // testar email do chamado concluido este teste confere assunto anexo do historico e hash no cabecalho
    @Test
    void enviaChamadoConcluidoComPdfAnexado() throws Exception {
        // preparar chamado concluido
        ChamadoDTO chamado = concluido(List.of(resposta(1L, "admin", "Resolvido")));

        // executar envio
        boolean enviado = emails.enviarEmailChamado(chamado);

        // verificar envio assunto anexo e hash
        assertTrue(enviado);
        verify(remetente).send(mensagem);
        assertEquals("CampusLink | Chamado #15 concluído", mensagem.getSubject());
        assertEquals(64, mensagem.getHeader("X-CampusLink-PDF-SHA256")[0].length());
        assertTrue(possuiAnexo(mensagem, "CampusLink-chamado-15-historico.pdf"));
    }

    // testar email do chamado aberto este teste confere o aviso de registro sem anexo
    @Test
    void enviaChamadoAbertoSemAnexo() throws Exception {
        // preparar chamado aberto
        ChamadoDTO chamado = concluido(List.of());
        chamado.setStatus("aberto");

        // executar envio
        boolean enviado = emails.enviarEmailChamado(chamado);

        // verificar envio sem anexo
        assertTrue(enviado);
        assertEquals("CampusLink | Chamado #15 registrado", mensagem.getSubject());
        assertFalse(possuiAnexo(mensagem, "CampusLink-chamado-15-historico.pdf"));
    }

    // testar falha no envio do chamado este teste confere que a falha do servidor de email nao interrompe o atendimento
    @Test
    void informaFalhaNoEnvioDoChamadoSemInterromper() {
        // preparar servidor de email fora do ar
        doThrow(new MailSendException("SMTP")).when(remetente).send(any(MimeMessage.class));

        // executar envio
        boolean enviado = emails.enviarEmailChamado(concluido(List.of()));

        // verificar falha informada
        assertFalse(enviado);
    }

    // testar email com codigo este teste confere assunto pela finalidade e a falha de envio repassada
    @Test
    void enviaCodigoPelaFinalidadeERepassaFalha() throws Exception {
        // preparar finalidade de redefinicao de senha

        // executar envio e envio com servidor fora do ar
        emails.enviarCodigo("ana@teste.com", "123456", FinalidadeCodigoEmail.REDEFINICAO_SENHA);
        String assunto = mensagem.getSubject();
        doThrow(new MailSendException("SMTP")).when(remetente).send(any(MimeMessage.class));
        MailSendException erro = assertThrows(MailSendException.class,
                () -> emails.enviarCodigo("ana@teste.com", "123456", FinalidadeCodigoEmail.ACESSO));

        // verificar assunto e falha
        assertEquals("CampusLink | Redefina sua senha", assunto);
        assertEquals("SMTP", erro.getMessage());
    }

    // conferir anexo este metodo procura um arquivo com o nome informado nas partes do email
    private boolean possuiAnexo(Part parte, String nome) throws Exception {
        if (nome.equals(parte.getFileName())) {
            return true;
        }
        if (parte.getContent() instanceof Multipart partes) {
            for (int i = 0; i < partes.getCount(); i++) {
                if (possuiAnexo(partes.getBodyPart(i), nome)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ler texto do pdf este metodo extrai o texto do documento gerado
    private String texto(byte[] arquivo) throws Exception {
        try (PDDocument documento = Loader.loadPDF(arquivo)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    // montar chamado concluido este metodo cria o chamado 15 da usuaria ana com as respostas informadas
    private ChamadoDTO concluido(List<ChamadoRespostaDTO> respostas) {
        ChamadoDTO chamado = new ChamadoDTO();
        chamado.setId(15L);
        chamado.setIdUsuario(2L);
        chamado.setNomeUsuario("Ana");
        chamado.setEmailUsuario("ana@teste.com");
        chamado.setAssunto("Erro no envio");
        chamado.setDescricao("Nao consigo enviar o arquivo");
        chamado.setStatus("concluido");
        chamado.setCreatedAt(ABERTURA);
        chamado.setConcluidoEm(ABERTURA.plusHours(3));
        chamado.setIdAdminConclusao(1L);
        chamado.setRespostas(new ArrayList<>(respostas));
        return chamado;
    }

    // montar resposta este metodo cria uma mensagem do administrador ou do usuario
    private ChamadoRespostaDTO resposta(Long id, String tipoAutor, String texto) {
        ChamadoRespostaDTO resposta = new ChamadoRespostaDTO();
        resposta.setId(id);
        resposta.setTipoAutor(tipoAutor);
        resposta.setNomeAutor("admin".equals(tipoAutor) ? "Suporte" : "Ana");
        resposta.setIdAdminUsuario("admin".equals(tipoAutor) ? 1L : null);
        resposta.setIdUsuario("admin".equals(tipoAutor) ? null : 2L);
        resposta.setResposta(texto);
        resposta.setCreatedAt(ABERTURA.plusHours(1));
        return resposta;
    }
}
