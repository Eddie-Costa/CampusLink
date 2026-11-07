// envio de mensagens entrega codigos dados pessoais e avisos de atendimento
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

// servico de email esta classe combina versoes em texto e pagina formatada com anexos quando necessario
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

    // enviar email de verificacao este metodo monta e envia o codigo conforme a finalidade em uma tarefa separada
    @Async("emailTaskExecutor")
    public void enviarCodigo(String para, String codigo, FinalidadeCodigoEmail finalidade) {

        String operacaoId = UUID.randomUUID().toString();
        logger.debug("Iniciando envio de email de 2FA. operacaoId={}", operacaoId);
        // registro de detalhes confere se esta habilitado ao enviar email de verificacao
        if (logger.isDebugEnabled()) {
            // gravar historico ao enviar email de verificacao tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Iniciando envio de email de 2FA. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        // enviar email de verificacao tenta montar e enviar a mensagem com o codigo
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            String textoSimples = finalidade.getTitulo() + "\n\n" + finalidade.getDescricao()
                    + "\n\nCódigo: " + codigo + "\nVálido por 5 minutos. Não compartilhe este código."
                    + "\n\nSe você não solicitou esta ação, ignore este e-mail.";
            prepararMensagem(mensagem, para, "CampusLink | " + finalidade.getTitulo(),
                    textoSimples, emailTemplateService.codigo(codigo, finalidade));

            mailSender.send(mensagem);

            logger.info("Email HTML de 2FA enviado com sucesso! operacaoId={}", operacaoId);
            // registro de informacoes confere se esta habilitado ao enviar email de verificacao
            if (logger.isInfoEnabled()) {
                // gravar historico ao enviar email de verificacao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Email HTML de 2FA enviado com sucesso! operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (MessagingException e) {

            logger.error("Erro ao montar o email HTML de 2FA. operacaoId={}", operacaoId, e);
            // registro de erros confere se esta habilitado ao enviar email de verificacao
            if (logger.isErrorEnabled()) {
                // gravar historico ao enviar email de verificacao tenta salvar o registro no banco e informa falhas
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
            // registro de erros confere se esta habilitado ao enviar email de verificacao
            if (logger.isErrorEnabled()) {
                // gravar historico ao enviar email de verificacao tenta salvar o registro no banco e informa falhas
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

    // enviar email de dados cadastrais este metodo monta e envia a copia dos dados pessoais do titular
    public void enviarEmailDados(DadosUsuarioDTO dadosUsuario) {

        String operacaoId = UUID.randomUUID().toString();
        logger.debug("Iniciando envio de email de exportação de dados. operacaoId={}", operacaoId);
        // registro de detalhes confere se esta habilitado ao enviar email de dados cadastrais
        if (logger.isDebugEnabled()) {
            // gravar historico ao enviar email de dados cadastrais tenta salvar o registro no banco e informa falhas
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

        // enviar dados pessoais por email tenta montar e enviar a exportacao dos dados do usuario
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            prepararMensagem(mensagem, dadosUsuario.getEmail(), "CampusLink | Seus dados pessoais",
                    corpoEmail.toString(), emailTemplateService.dados(dadosUsuario));
            mailSender.send(mensagem);
        } catch (MessagingException | MailException e) {
            logger.error("Erro ao enviar email de exportação de dados. operacaoId={}", operacaoId, e);
            // registro de erros confere se esta habilitado ao enviar email de dados cadastrais
            if (logger.isErrorEnabled()) {
                // gravar historico ao enviar email de dados cadastrais tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Erro ao enviar email de exportação de dados. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            // preservar erro de envio repassa a falha do servico de email
            if (e instanceof MailException mailException) {
                throw mailException;
            }
            throw new MailPreparationException("Erro ao montar o email de exportação de dados", e);
        }

        logger.info("Email de exportação de dados enviado com sucesso! operacaoId={}", operacaoId);
        // registro de informacoes confere se esta habilitado ao enviar email de dados cadastrais
        if (logger.isInfoEnabled()) {
            // gravar historico ao enviar email de dados cadastrais tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Email de exportação de dados enviado com sucesso! operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    // enviar email do chamado este metodo confirma a abertura ou envia a conclusao com o historico em anexo
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
        // incluir descricao do chamado acrescenta a solicitacao inicial no email de abertura
        if (!concluido) texto.append("\n").append(chamado.getDescricao()).append("\n");
        texto.append("\nAcompanhe em Meus chamados no CampusLink.");

        // enviar email do chamado tenta montar a mensagem e anexar o historico quando concluido
        try {

            byte[] pdf = concluido ? chamadoPdfService.gerar(chamado) : null;
            String hashPdf = pdf == null ? null : chamadoPdfService.sha256(pdf);
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = prepararMensagem(mensagem, chamado.getEmailUsuario(),
                    "CampusLink | Chamado #" + chamado.getId() + (concluido ? " concluído" : " registrado"),
                    texto.toString(), emailTemplateService.chamado(chamado));
            // anexar historico do chamado inclui o pdf e seu codigo de integridade no email
            if (pdf != null) {
                mensagem.setHeader("X-CampusLink-PDF-SHA256", hashPdf);
                helper.addAttachment("CampusLink-chamado-" + chamado.getId() + "-historico.pdf", new ByteArrayResource(pdf), "application/pdf");
            }
            mailSender.send(mensagem);
            // registrar envio do historico registra a aceitacao do email com o codigo do pdf
            if (hashPdf != null) logger.info("Email de conclusão aceito pelo SMTP. chamado={} pdfSha256={}", chamado.getId(), hashPdf);
            return true;
        } catch (MessagingException | MailException e) {
            logger.error("Erro ao enviar email do chamado {}. status={}", chamado.getId(), chamado.getStatus(), e);
            return false;
        }
    }

    // preparar mensagem de email este metodo define remetente destinatario assunto texto e logo
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
