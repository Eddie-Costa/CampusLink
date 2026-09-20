package com.example.CampusLink.service;

import com.example.CampusLink.dao.usuarioDAO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.helpers.MessageFormatter;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class emailService {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(emailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Async("emailTaskExecutor")
    public void enviarCodigo(String para, String codigo) {

        String operacaoId = UUID.randomUUID().toString();
        logger.debug("Iniciando envio de email de 2FA. operacaoId={}", operacaoId);
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Iniciando envio de email de 2FA. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        try {
            MimeMessage mensagem =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mensagem,
                            MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                            StandardCharsets.UTF_8.name()
                    );

            helper.setFrom("tccumcriqedmat@gmail.com");
            helper.setTo(para);
            helper.setSubject(
                    "Seu código de verificação - CampusLink"
            );

            String textoSimples =
                    "Seu código de verificação do CampusLink é: "
                            + codigo
                            + ". Este código expira em 5 minutos.";

            String corpoHtml = """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport"
                              content="width=device-width, initial-scale=1.0">
                        <title>Verificação CampusLink</title>
                    </head>

                    <body style="
                        margin: 0;
                        padding: 0;
                        background-color: #eef2f7;
                        font-family: Arial, Helvetica, sans-serif;
                    ">

                        <table role="presentation"
                               width="100%"
                               cellspacing="0"
                               cellpadding="0"
                               border="0"
                               style="
                                   width: 100%;
                                   background-color: #eef2f7;
                               ">

                            <tr>
                                <td align="center"
                                    style="padding: 35px 15px;">

                                    <table role="presentation"
                                           width="100%"
                                           cellspacing="0"
                                           cellpadding="0"
                                           border="0"
                                           style="
                                               width: 100%;
                                               max-width: 600px;
                                               overflow: hidden;
                                               background-color: #ffffff;
                                               border-radius: 14px;
                                               box-shadow: 0 4px 15px
                                                   rgba(17, 24, 63, 0.15);
                                           ">

                                        <tr>
                                            <td align="center"
                                                style="
                                                    padding: 28px 20px;
                                                    background-color: #11183f;
                                                    color: #ffffff;
                                                ">

                                                <h1 style="
                                                    margin: 0;
                                                    font-size: 28px;
                                                    letter-spacing: 1px;
                                                ">
                                                    CampusLink
                                                </h1>

                                                <p style="
                                                    margin: 8px 0 0;
                                                    color: #dce5ff;
                                                    font-size: 14px;
                                                ">
                                                    integração para sua vida acadêmica
                                                </p>

                                            </td>
                                        </tr>

                                        <tr>
                                            <td style="
                                                padding: 35px 32px;
                                                color: #27314f;
                                            ">

                                                <h2 style="
                                                    margin: 0 0 18px;
                                                    color: #11183f;
                                                    font-size: 23px;
                                                    text-align: center;
                                                ">
                                                    Código de verificação
                                                </h2>

                                                <p style="
                                                    margin: 0 0 20px;
                                                    font-size: 15px;
                                                    line-height: 1.6;
                                                    text-align: center;
                                                ">
                                                    Recebemos uma solicitação
                                                    de acesso à sua conta.
                                                </p>

                                                <p style="
                                                    margin: 0 0 12px;
                                                    font-size: 14px;
                                                    text-align: center;
                                                ">
                                                    Digite o código abaixo
                                                    na página de verificação:
                                                </p>

                                                <div style="
                                                    margin: 25px 0;
                                                    padding: 18px;
                                                    border-radius: 10px;
                                                    background-color: #eef2ff;
                                                    color: #11183f;
                                                    font-size: 32px;
                                                    font-weight: bold;
                                                    letter-spacing: 8px;
                                                    text-align: center;
                                                ">
                                                    {{CODIGO}}
                                                </div>

                                                <p style="
                                                    margin: 0;
                                                    color: #596584;
                                                    font-size: 13px;
                                                    line-height: 1.6;
                                                    text-align: center;
                                                ">
                                                    Este código é válido por
                                                    <strong>5 minutos</strong>.
                                                </p>

                                                <p style="
                                                    margin: 18px 0 0;
                                                    color: #596584;
                                                    font-size: 13px;
                                                    line-height: 1.6;
                                                    text-align: center;
                                                ">
                                                    Se você não realizou essa
                                                    solicitação, ignore este e-mail.
                                                </p>

                                            </td>
                                        </tr>

                                        <tr>
                                            <td align="center"
                                                style="
                                                    padding: 18px;
                                                    background-color: #f5f7fb;
                                                    color: #737b89;
                                                    font-size: 12px;
                                                ">

                                                Este é um e-mail automático.
                                                Não responda esta mensagem.

                                            </td>
                                        </tr>

                                    </table>

                                </td>
                            </tr>

                        </table>

                    </body>
                    </html>
                    """.replace("{{CODIGO}}", codigo);

            helper.setText(
                    textoSimples,
                    corpoHtml
            );

            mailSender.send(mensagem);

            logger.info("Email HTML de 2FA enviado com sucesso! operacaoId={}", operacaoId);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
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
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Erro ao montar o email HTML de 2FA. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
        } catch (MailException e) {
            logger.error("Erro ao enviar email de 2FA. operacaoId={}", operacaoId, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", emailService.class.getName(), "enviarCodigo", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Erro ao enviar email de 2FA. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }

    @Async("emailTaskExecutor")
    public void enviarEmailDados(
            String para,
            String Nome,
            String Sobrenome,
            String Email,
            String DT_Reg
    ) {

        String operacaoId = UUID.randomUUID().toString();
        logger.debug("Iniciando envio de email de exportação de dados. operacaoId={}", operacaoId);
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Iniciando envio de email de exportação de dados. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        SimpleMailMessage mensagem =
                new SimpleMailMessage();

        mensagem.setFrom("tccumcriqedmat@gmail.com");
        mensagem.setTo(para);
        mensagem.setSubject(
                "Dados pessoais - Exportação de dados"
        );

        String corpoEmail =
                "Nome: " + Nome + "\n"
                        + "Sobrenome: " + Sobrenome + "\n"
                        + "Email: " + Email + "\n"
                        + "Data de Registro: " + DT_Reg;

        mensagem.setText(corpoEmail);

        try {
            mailSender.send(mensagem);
        } catch (MailException e) {
            logger.error("Erro ao enviar email de exportação de dados. operacaoId={}", operacaoId, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                            MessageFormatter.arrayFormat("Erro ao enviar email de exportação de dados. operacaoId={}", new Object[]{operacaoId}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }

        logger.info("Email de exportação de dados enviado com sucesso! operacaoId={}", operacaoId);
        if (logger.isInfoEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "INFO", emailService.class.getName(), "enviarEmailDados", UUID.fromString(operacaoId),
                        MessageFormatter.arrayFormat("Email de exportação de dados enviado com sucesso! operacaoId={}", new Object[]{operacaoId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }
}
