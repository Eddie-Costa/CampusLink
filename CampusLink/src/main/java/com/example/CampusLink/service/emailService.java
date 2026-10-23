package com.example.CampusLink.service;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.dto.ChamadoDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.helpers.MessageFormatter;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class emailService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(emailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private ChamadoPdfService chamadoPdfService;

    @Async("emailTaskExecutor")
    public void enviarCodigo(String para, String codigo, FinalidadeCodigoEmail finalidade) {

        String operacaoId = UUID.randomUUID().toString();
        logger.debug("Iniciando envio de email de 2FA. operacaoId={}", operacaoId);
        if (logger.isDebugEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Iniciando envio de email de 2FA. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            String textoSimples = finalidade.getTitulo() + "\n\n" + finalidade.getDescricao()
                    + "\n\nCódigo: " + codigo + "\nVálido por 5 minutos. Não compartilhe este código."
                    + "\n\nSe você não solicitou esta ação, ignore este e-mail.";
            prepararMensagem(mensagem, para, "CampusLink | " + finalidade.getTitulo(),
                    textoSimples, emailTemplateService.codigo(codigo, finalidade));

            mailSender.send(mensagem);

            logger.info("Email HTML de 2FA enviado com sucesso! operacaoId={}", operacaoId);
            if (logger.isInfoEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Email HTML de 2FA enviado com sucesso! operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (MessagingException e) {

            logger.error("Erro ao montar o email HTML de 2FA. operacaoId={}", operacaoId, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Erro ao montar o email HTML de 2FA. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new MailPreparationException("Erro ao montar o email de verificação", e);
        } catch (MailException e) {
            logger.error("Erro ao enviar email de 2FA. operacaoId={}", operacaoId, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Erro ao enviar email de 2FA. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }

    public void enviarEmailDados(DadosUsuarioDTO dadosUsuario) {

        String operacaoId = UUID.randomUUID().toString();
        logger.debug("Iniciando envio de email de exportação de dados. operacaoId={}", operacaoId);
        if (logger.isDebugEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Iniciando envio de email de exportação de dados. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        StringBuilder corpoEmail = new StringBuilder("Seus dados pessoais\n\n");
        emailTemplateService.camposDados(dadosUsuario).forEach((rotulo, valor) ->
                corpoEmail.append(rotulo).append(": ").append(valor).append("\n"));
        corpoEmail.append("\nPara outras solicitações sobre seus dados, consulte os Termos de Uso na plataforma.");

        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            prepararMensagem(mensagem, dadosUsuario.getEmail(), "CampusLink | Seus dados pessoais",
                    corpoEmail.toString(), emailTemplateService.dados(dadosUsuario));
            mailSender.send(mensagem);
        } catch (MessagingException | MailException e) {
            logger.error("Erro ao enviar email de exportação de dados. operacaoId={}", operacaoId, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Erro ao enviar email de exportação de dados. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            if (e instanceof MailException mailException) {
                throw mailException;
            }
            throw new MailPreparationException("Erro ao montar o email de exportação de dados", e);
        }

        logger.info("Email de exportação de dados enviado com sucesso! operacaoId={}", operacaoId);
        if (logger.isInfoEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Email de exportação de dados enviado com sucesso! operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    public boolean enviarEmailChamado(ChamadoDTO chamado) {
        boolean concluido = "concluido".equals(chamado.getStatus());
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.of("America/Sao_Paulo"));
        StringBuilder texto = new StringBuilder();
        texto.append(concluido ? "Chamado concluído\n\n" : "Chamado registrado\n\n");
        texto.append(concluido ? "Seu atendimento foi concluído. O histórico completo está no PDF anexado.\n\n"
                : "Recebemos sua solicitação. Nossa equipe responderá pelo CampusLink.\n\n");
        texto.append("Protocolo: #").append(chamado.getId()).append("\n");
        texto.append("Assunto: ").append(chamado.getAssunto()).append("\n");
        texto.append(concluido ? "Concluído em: " : "Aberto em: ")
                .append(formato.format(concluido ? chamado.getConcluidoEm() : chamado.getCreatedAt()))
                .append(" (Brasília)\n");
        if (!concluido) texto.append("\n").append(chamado.getDescricao()).append("\n");
        texto.append("\nAcompanhe em Meus chamados no CampusLink.");

        try {
            // gera o anexo antes de enviar, para nunca confirmar um envio sem o histórico
            byte[] pdf = concluido ? chamadoPdfService.gerar(chamado) : null;
            String hashPdf = pdf == null ? null : chamadoPdfService.sha256(pdf);
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = prepararMensagem(mensagem, chamado.getEmailUsuario(),
                    "CampusLink | Chamado #" + chamado.getId() + (concluido ? " concluído" : " registrado"),
                    texto.toString(), emailTemplateService.chamado(chamado));
            if (pdf != null) {
                mensagem.setHeader("X-CampusLink-PDF-SHA256", hashPdf);
                helper.addAttachment("CampusLink-chamado-" + chamado.getId() + "-historico.pdf", new ByteArrayResource(pdf), "application/pdf");
            }
            mailSender.send(mensagem);
            if (hashPdf != null) logger.info("Email de conclusão aceito pelo SMTP. chamado={} pdfSha256={}", chamado.getId(), hashPdf);
            return true;
        } catch (MessagingException | MailException e) {
            logger.error("Erro ao enviar email do chamado {}. status={}", chamado.getId(), chamado.getStatus(), e);
            return false;
        }
    }

    private MimeMessageHelper prepararMensagem(MimeMessage mensagem, String para, String assunto,
                                               String texto, String html) throws MessagingException {
        MimeMessageHelper helper = new MimeMessageHelper(mensagem, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name());
        helper.setFrom("tccumcriqedmat@gmail.com");
        helper.setTo(para);
        helper.setSubject(assunto);
        helper.setText(texto, html);
        helper.addInline("campuslink-logo", new ClassPathResource("static/images/LogoCampusLink.png"), "image/png");
        return helper;
    }
}
